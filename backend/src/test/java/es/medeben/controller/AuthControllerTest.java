package es.medeben.controller;

import es.medeben.config.SecurityConfig;
import es.medeben.domain.usuario.Usuario;
import es.medeben.service.AuthService;
import es.medeben.service.CredencialesInvalidasException;
import es.medeben.service.EmailYaRegistradoException;
import es.medeben.service.TokenEmitido;
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
                .thenReturn(new Usuario("trabajador@example.com", "hash"));

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
    @DisplayName("registro con email duplicado → 409")
    void registroDuplicado() throws Exception {
        when(authService.registra(anyString(), anyString()))
                .thenThrow(new EmailYaRegistradoException());

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"una-contraseña-larga"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /auth/login correcto → token y expiración")
    void login() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenReturn(new TokenEmitido("un.jwt.firmado", Instant.parse("2026-07-09T12:00:00Z")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"una-contraseña-larga"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("un.jwt.firmado"));
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
    @DisplayName("GET /me sin token → 401; con JWT → email del token")
    void me() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(j -> j.claim("email", "trabajador@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("trabajador@example.com"));
    }
}
