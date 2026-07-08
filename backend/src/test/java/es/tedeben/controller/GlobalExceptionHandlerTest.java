package es.tedeben.controller;

import es.tedeben.config.SecurityConfig;
import es.tedeben.repository.ConvenioCatalog;
import es.tedeben.service.CalculoConvenioService;
import es.tedeben.service.TablaSalarialService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El camino del 500: datos internos corruptos se registran en el log pero
 * NUNCA salen en la respuesta (mensaje saneado). Servicios mockeados para
 * poder provocar la excepción.
 */
@WebMvcTest(CalculoController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConvenioCatalog convenios;

    @MockitoBean
    private CalculoConvenioService calculo;

    @MockitoBean
    private TablaSalarialService tablas;

    @Test
    @DisplayName("IllegalStateException interna → 500 saneado, sin filtrar el detalle")
    void errorInternoSaneado() throws Exception {
        when(tablas.salarioBaseMinimo(anyString(), anyMap(), any()))
                .thenThrow(new IllegalStateException("Capa derivada ambigua: detalle interno secreto"));

        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","fecha":"2026-07-08",
                                 "dimensiones":{"nivel":"III"}}"""))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Error interno de datos del convenio"))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("secreto"))));
    }
}
