package es.medeben.controller;

import es.medeben.config.SecurityConfig;
import es.medeben.domain.usuario.Perfil;
import es.medeben.service.PerfilService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PerfilController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PerfilControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final java.time.OffsetDateTime SELLO =
            java.time.OffsetDateTime.parse("2026-07-08T10:15:00+02:00");

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
    @DisplayName("sin token → 401 RFC 7807 (el perfil es privado)")
    void sinToken() throws Exception {
        mockMvc.perform(get("/api/v1/perfil"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Autenticación requerida"))
                .andExpect(jsonPath("$.instance").value("/api/v1/perfil"));
    }

    @Test
    @DisplayName("token inválido → el mismo 401 neutro que sin token (no se filtra el porqué)")
    void tokenInvalido() throws Exception {
        when(jwtDecoder.decode("basura")).thenThrow(
                new org.springframework.security.oauth2.jwt.BadJwtException("firma incorrecta"));

        mockMvc.perform(get("/api/v1/perfil").header("Authorization", "Bearer basura"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Autenticación requerida"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("firma"))));
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
                "cocinero", Map.of("nivel", "III"), null, null, SELLO);
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
    @DisplayName("GET con perfil existente → 200 con el perfil serializado")
    void getConPerfil() throws Exception {
        Perfil existente = new Perfil(USUARIO, "Madrid", "hosteleria", "madrid-hosteleria",
                "cocinero", Map.of("nivel", "III"), new java.math.BigDecimal("1400.00"), null, SELLO);
        when(perfilService.busca(USUARIO)).thenReturn(Optional.of(existente));

        mockMvc.perform(get("/api/v1/perfil").with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convenioId").value("madrid-hosteleria"))
                .andExpect(jsonPath("$.dimensiones.nivel").value("III"))
                .andExpect(jsonPath("$.salarioBaseMensual").value(1400.00));
    }

    @Test
    @DisplayName("PUT con más de 10 dimensiones → 400")
    void demasiadasDimensiones() throws Exception {
        StringBuilder dims = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            if (i > 0) dims.append(',');
            dims.append("\"d").append(i).append("\":\"v\"");
        }
        mockMvc.perform(put("/api/v1/perfil").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provincia\":\"Madrid\",\"subsector\":\"hosteleria\",\"dimensiones\":{" + dims + "}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT con un valor de dimensión gigante → 400 en el borde (validación del DTO)")
    void dimensionConValorGigante() throws Exception {
        mockMvc.perform(put("/api/v1/perfil").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provincia\":\"Madrid\",\"subsector\":\"hosteleria\","
                                + "\"dimensiones\":{\"nivel\":\"" + "x".repeat(401) + "\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT con una clave de dimensión gigante → 400 en el borde (validación del DTO)")
    void dimensionConClaveGigante() throws Exception {
        mockMvc.perform(put("/api/v1/perfil").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provincia\":\"Madrid\",\"subsector\":\"hosteleria\","
                                + "\"dimensiones\":{\"" + "k".repeat(41) + "\":\"III\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT con dimensión que no existe en el convenio → 422 RFC 7807 con el detalle del validador")
    void dimensionDesconocida() throws Exception {
        when(perfilService.guarda(eq(USUARIO), anyString(), anyString(), any(), any(), any(), any()))
                .thenThrow(new DimensionDesconocidaException(
                        "El valor 'ZZ' no existe para la dimensión 'nivel' del convenio 'madrid-hosteleria'"));

        mockMvc.perform(put("/api/v1/perfil").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provincia":"Madrid","subsector":"hosteleria","dimensiones":{"nivel":"ZZ"}}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("nivel"),
                                org.hamcrest.Matchers.containsString("ZZ"))));
    }

    @Test
    @DisplayName("token con subject que no es UUID → 401 genérico, sin eco del valor")
    void subjectRaro() throws Exception {
        mockMvc.perform(get("/api/v1/perfil")
                        .with(jwt().jwt(j -> j.subject("no-soy-un-uuid"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Token inválido"));
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
