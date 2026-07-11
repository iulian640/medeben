package es.medeben.controller;

import es.medeben.config.SecurityConfig;
import es.medeben.repository.ConvenioCatalog;
import es.medeben.service.CalculoConvenioService;
import es.medeben.service.FichajeService;
import es.medeben.service.TablaSalarialService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Robustez de la API ante entradas raras (issue #232): los errores de
 * validación del cuerpo deben salir como RFC 7807 con el campo y el motivo
 * en castellano — igual que las validaciones hechas a mano en los servicios —
 * y nunca con el genérico "Invalid request content." del framework.
 */
@WebMvcTest(controllers = {FichajeController.class, CalculoController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ApiRobustezTest {

    private static final UUID USUARIO = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FichajeService fichajeService;

    @MockitoBean
    private ConvenioCatalog convenios;

    @MockitoBean
    private CalculoConvenioService calculo;

    @MockitoBean
    private TablaSalarialService tablas;

    @MockitoBean
    private es.medeben.service.SmiService smi;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor comoUsuario() {
        return jwt().jwt(j -> j.subject(USUARIO.toString()));
    }

    // --- (d) Errores de Bean Validation: campo + motivo en castellano ---

    @Test
    @DisplayName("hora inválida en el body → 400 RFC 7807 que dice el campo y el motivo en castellano")
    void validacionConCampoYMotivo() throws Exception {
        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha":"2026-07-08","tipo":"SALIDA","hora":"24:00"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("hora: hora en formato HH:mm"));
    }

    @Test
    @DisplayName("falta la fecha → 400 con el campo y el motivo en castellano, no \"Invalid request content.\"")
    void faltaLaFecha() throws Exception {
        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"SALIDA","hora":"23:00"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("fecha: no puede faltar"));
    }

    @Test
    @DisplayName("varios campos mal a la vez → todos en el detalle, en orden estable")
    void variosCamposInvalidos() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "convenioId: no puede faltar; dimensiones: no puede faltar; fecha: no puede faltar"));
    }

    // --- (a) Carácter NUL en campos de texto: 400 de validación, nunca 500 ---

    @Test
    @DisplayName("motivo con carácter NUL → 400 de validación (antes reventaba en Postgres con 500)")
    void motivoConNul() throws Exception {
        // \\u0000 llega como escape JSON y Jackson lo convierte en el carácter
        // NUL real, el que Postgres rechaza en cualquier columna de texto.
        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha":"2026-07-08","tipo":"AUSENCIA","hora":null,"motivo":"baja m\\u0000dica"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("motivo: no puede contener el carácter nulo (U+0000)"));
    }

    @Test
    @DisplayName("NUL en una dimensión del cálculo anónimo → 400, mismo cierre uniforme")
    void dimensionConNul() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","fecha":"2026-07-08",
                                 "dimensiones":{"nivel":"II\\u0000I"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("no puede contener el carácter nulo")));
    }

    @Test
    @DisplayName("password corta en el registro → motivo en castellano con los límites")
    void passwordCorta() throws Exception {
        // El slice no carga AuthController: se valida el DTO equivalente en
        // /calculo (mismo mecanismo global). El importe negativo cubre @Positive.
        mockMvc.perform(post("/api/v1/calculo/horas-extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","anio":2026,
                                 "salarioBaseMensual":-1250.91,"plusesAnuales":0,"horas":5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("salarioBaseMensual: debe ser mayor que 0"));
    }
}
