package es.medeben.controller;

import es.medeben.config.SecurityConfig;
import es.medeben.domain.usuario.Usuario;
import es.medeben.service.AuthService;
import es.medeben.service.CredencialesInvalidasException;
import es.medeben.service.SesionEmitida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("POST /auth/registro válido → 201 con el email (nunca la contraseña)")
    void registro() throws Exception {
        when(authService.registra(anyString(), anyString()))
                .thenReturn("trabajador@example.com");

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"una-contraseña-larga"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("trabajador@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("registro con contraseña corta → 400 (validación)")
    void registroPasswordCorta() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"corta"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ANTI-ENUMERACIÓN: el registro con email ya usado responde el MISMO 201 que uno nuevo (el 409 murió)")
    void registroEmailDuplicadoDevuelve201Uniforme() throws Exception {
        // El servicio ya no distingue hacia fuera: siempre devuelve el email.
        when(authService.registra(anyString(), anyString()))
                .thenReturn("trabajador@example.com");

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"una-contraseña-larga"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("trabajador@example.com"));
    }

    @Test
    @DisplayName("POST /auth/verifica-email con token válido → 200 (token en el BODY, nunca en la URL del API)")
    void verificaEmailValido() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verifica-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token-del-correo"}"""))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(authService).verificaEmail("token-del-correo");
    }

    @Test
    @DisplayName("verifica-email con token inválido/caducado → 400 con detalle FIJO (sin decir el motivo)")
    void verificaEmailInvalido() throws Exception {
        org.mockito.Mockito.doThrow(new es.medeben.service.VerificacionInvalidaException())
                .when(authService).verificaEmail(anyString());

        mockMvc.perform(post("/api/v1/auth/verifica-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"caducado-o-falso"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("El enlace de verificación no es válido o ha caducado"));
    }

    @Test
    @DisplayName("verifica-email sin token → 400 de validación")
    void verificaEmailSinToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verifica-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ANTI-ENUMERACIÓN: POST /auth/reenvia-verificacion responde 202 sea cual sea el email")
    void reenviaVerificacionUniforme() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reenvia-verificacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cualquiera@example.com"}"""))
                .andExpect(status().isAccepted());
        org.mockito.Mockito.verify(authService).reenviaVerificacion("cualquiera@example.com");
    }

    @Test
    @DisplayName("POST /auth/login correcto → access, expiración y refresh (B4)")
    void login() throws Exception {
        when(authService.login(anyString(), anyString())).thenReturn(sesionDePrueba());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"una-contraseña-larga"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("un.jwt.firmado"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-opaco"))
                .andExpect(jsonPath("$.refreshExpiraEn").exists());
    }

    @Test
    @DisplayName("POST /auth/refresh sin access token → 200 con la sesión rotada (público, como el login)")
    void refresh() throws Exception {
        when(authService.refresca("refresh-opaco")).thenReturn(sesionDePrueba());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-opaco"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("un.jwt.firmado"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-opaco"))
                .andExpect(jsonPath("$.refreshExpiraEn").exists());
    }

    @Test
    @DisplayName("refresh inválido/reutilizado → 401 idéntico al de credenciales (sin oráculo)")
    void refreshInvalido() throws Exception {
        when(authService.refresca(anyString())).thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"robado-o-caducado"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh sin body válido → 400, el servicio ni se llama")
    void refreshSinToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refresca(anyString());
    }

    @Test
    @DisplayName("POST /auth/logout → 204 siempre (idempotente, revoca en servidor)")
    void logout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-opaco"}"""))
                .andExpect(status().isNoContent());

        verify(authService).cierraSesion("refresh-opaco");
    }

    @Test
    @DisplayName("logout sin body válido → 400, el servicio ni se llama (mismo @Valid que el refresh)")
    void logoutSinToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).cierraSesion(anyString());
    }

    private static SesionEmitida sesionDePrueba() {
        return new SesionEmitida("un.jwt.firmado", Instant.parse("2026-07-09T12:00:00Z"),
                "refresh-opaco", Instant.parse("2026-07-16T12:00:00Z"));
    }

    @Test
    @DisplayName("login con credenciales malas → 401 con mensaje único (anti enumeración)")
    void loginIncorrecto() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"mala"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos"));
    }

    @Test
    @DisplayName("GET /me sin token → 401; con JWT → email y emailVerificado FRESCOS de BD (no del claim: tablet compartida)")
    void me() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());

        java.util.UUID usuarioId = java.util.UUID.randomUUID();
        when(authService.me(usuarioId))
                .thenReturn(new es.medeben.dto.MeResponse("trabajador@example.com", false));

        mockMvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(j -> j.subject(usuarioId.toString())
                                .claim("email", "trabajador@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("trabajador@example.com"))
                .andExpect(jsonPath("$.emailVerificado").value(false));
    }
}
