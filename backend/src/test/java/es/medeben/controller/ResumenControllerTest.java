package es.medeben.controller;

import es.medeben.config.SecurityConfig;
import es.medeben.domain.fichaje.EstadoDia;
import es.medeben.service.Cita;
import es.medeben.service.ImporteEstimadoMensual;
import es.medeben.service.ResumenMensual;
import es.medeben.service.ResumenMensualService;
import es.medeben.service.TopeAnualResumen;
import es.medeben.service.ValorHoraCalculado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumenController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ResumenControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResumenMensualService resumenService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor comoUsuario() {
        return jwt().jwt(j -> j.subject(USUARIO.toString()).claim("email", "t@example.com"));
    }

    private static ResumenMensual resumenEjemplo() {
        ValorHoraCalculado desglose = new ValorHoraCalculado(new BigDecimal("10.8979"),
                new BigDecimal("1250.91"), new BigDecimal("14"), BigDecimal.ZERO,
                new BigDecimal("1800"), false, List.of());
        ImporteEstimadoMensual importe = new ImporteEstimadoMensual(new BigDecimal("3.00"),
                new BigDecimal("10.90"), new BigDecimal("32.70"), new BigDecimal("1250.91"), false,
                desglose, List.of(new Cita("Salario base mínimo (Art. 20 del convenio)", "https://bocm.es")));
        TopeAnualResumen tope = new TopeAnualResumen(80, new BigDecimal("3.00"),
                List.of(new Cita("Tope de 80 h (art. 35.2 ET)", Cita.URL_ESTATUTO_TRABAJADORES)));
        return new ResumenMensual(YearMonth.of(2026, 7), 960, 1140, 180, 0, 0,
                Map.of(EstadoDia.Estado.COMPLETO, 2, EstadoDia.Estado.HUECO, 29),
                importe, tope, List.of());
    }

    @Test
    @DisplayName("sin token → 401 RFC 7807 (el resumen es privado)")
    void sinToken() throws Exception {
        mockMvc.perform(get("/api/v1/resumen/mes/2026-07"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET con token → 200 con el contrato JSON completo y sin caché pública (datos personales)")
    void contratoJson() throws Exception {
        when(resumenService.delMes(eq(USUARIO), eq(YearMonth.of(2026, 7)))).thenReturn(resumenEjemplo());

        mockMvc.perform(get("/api/v1/resumen/mes/2026-07").with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.mes").value("2026-07"))
                .andExpect(jsonPath("$.minutosTeoricos").value(960))
                .andExpect(jsonPath("$.minutosReales").value(1140))
                .andExpect(jsonPath("$.horasExtra.minutos").value(180))
                .andExpect(jsonPath("$.horasExtra.horas").value(3.00))
                .andExpect(jsonPath("$.deficitInformativo.minutos").value(0))
                .andExpect(jsonPath("$.diasSinCalcular").value(0))
                .andExpect(jsonPath("$.importeEstimado.importe").value(32.70))
                .andExpect(jsonPath("$.importeEstimado.precioHora").value(10.90))
                .andExpect(jsonPath("$.importeEstimado.salarioBaseAplicado").value(1250.91))
                .andExpect(jsonPath("$.importeEstimado.salarioRealUsado").value(false))
                .andExpect(jsonPath("$.importeEstimado.desglose.valorHora").value(10.8979))
                .andExpect(jsonPath("$.importeEstimado.citas").isNotEmpty())
                .andExpect(jsonPath("$.topeAnual.horas").value(80))
                .andExpect(jsonPath("$.topeAnual.acumuladoAnioHoras").value(3.00))
                .andExpect(jsonPath("$.topeAnual.citas").isNotEmpty())
                .andExpect(jsonPath("$.contadoresPorEstado.COMPLETO").value(2))
                .andExpect(jsonPath("$.contadoresPorEstado.HUECO").value(29))
                .andExpect(jsonPath("$.contadoresPorEstado.PENDIENTE").value(0))
                .andExpect(jsonPath("$.avisos").isArray());
    }

    @Test
    @DisplayName("el id del usuario sale del token, nunca de la petición")
    void idSaleDelToken() throws Exception {
        when(resumenService.delMes(eq(USUARIO), any())).thenReturn(resumenEjemplo());

        mockMvc.perform(get("/api/v1/resumen/mes/2026-07").with(comoUsuario()))
                .andExpect(status().isOk());

        verify(resumenService).delMes(eq(USUARIO), eq(YearMonth.of(2026, 7)));
    }

    @Test
    @DisplayName("faltan datos (sin horario) → 422 RFC 7807 con detalle claro de qué falta")
    void datosIncompletos422() throws Exception {
        when(resumenService.delMes(eq(USUARIO), any())).thenThrow(
                new ResumenIncompletoException("No has definido tu horario para ese mes"));

        mockMvc.perform(get("/api/v1/resumen/mes/2026-07").with(comoUsuario()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(containsString("horario")));
    }

    @Test
    @DisplayName("mes con mes fuera de rango (2026-13) → 400 (formato yyyy-MM inválido)")
    void mesFueraDeRango() throws Exception {
        mockMvc.perform(get("/api/v1/resumen/mes/2026-13").with(comoUsuario()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("mes con texto no numérico → 400")
    void mesNoNumerico() throws Exception {
        mockMvc.perform(get("/api/v1/resumen/mes/julio").with(comoUsuario()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("mes anterior a 2019 (registro horario obligatorio) → 400 con la cota explicada (review)")
    void mesDemasiadoAntiguo() throws Exception {
        mockMvc.perform(get("/api/v1/resumen/mes/2018-12").with(comoUsuario()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("2019")));
    }

    @Test
    @DisplayName("el input crudo del path no se ecoa en el error 400 (review): ni en detail ni en logs vía detail")
    void inputCrudoNoSeEcoa() throws Exception {
        String raro = "x".repeat(64);
        mockMvc.perform(get("/api/v1/resumen/mes/" + raro).with(comoUsuario()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(not(containsString("xxxx"))));
    }
}
