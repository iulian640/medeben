package es.tedeben.controller;

import es.tedeben.config.SecurityConfig;
import es.tedeben.domain.usuario.Perfil;
import es.tedeben.service.PerfilService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PerfilController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PerfilControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PerfilService perfilService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor comoUsuario() {
        return jwt().jwt(j -> j.subject(USUARIO.toString()).claim("email", "t@example.com"));
    }

    @Test
    @DisplayName("sin token → 401 (el perfil es privado)")
    void sinToken() throws Exception {
        mockMvc.perform(get("/api/v1/perfil")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET con token y sin perfil creado → 404 amable")
    void sinPerfil() throws Exception {
        when(perfilService.busca(USUARIO)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/perfil").with(comoUsuario()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT guarda el perfil del usuario del token (el id nunca viene del body)")
    void guardaPerfil() throws Exception {
        Perfil guardado = new Perfil(USUARIO, "Madrid", "hosteleria", "madrid-hosteleria",
                "cocinero", Map.of("nivel", "III"), null, null);
        when(perfilService.guarda(eq(USUARIO), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(guardado);

        mockMvc.perform(put("/api/v1/perfil").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provincia":"Madrid","subsector":"hosteleria","puestoId":"cocinero",
                                 "dimensiones":{"nivel":"III"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convenioId").value("madrid-hosteleria"));
    }

    @Test
    @DisplayName("PUT sin provincia → 400 (validación)")
    void validacion() throws Exception {
        mockMvc.perform(put("/api/v1/perfil").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subsector":"hosteleria"}"""))
                .andExpect(status().isBadRequest());
    }
}
