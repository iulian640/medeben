package es.tedeben.controller;

import es.tedeben.config.SecurityConfig;
import es.tedeben.repository.ConvenioCatalog;
import es.tedeben.repository.HechosCatalog;
import es.tedeben.service.CalculoConvenioService;
import es.tedeben.service.TablaSalarialService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CalculoController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        ConvenioCatalog.class, HechosCatalog.class,
        CalculoConvenioService.class, TablaSalarialService.class})
class CalculoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("POST /api/v1/calculo/horas-extra: caso piloto Madrid, 5 h → 54,49 € con citas")
    void horasExtraMadrid() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/horas-extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","anio":2026,
                                 "salarioBaseMensual":1250.91,"plusesAnuales":2103.42,"horas":5}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importe").value(54.49))
                .andExpect(jsonPath("$.desglose.valorHora").value(10.8979))
                .andExpect(jsonPath("$.desglose.mensualidades").value(14))
                .andExpect(jsonPath("$.desglose.divisorHoras").value(1800))
                .andExpect(jsonPath("$.desglose.esDivisorExplicito").value(false))
                .andExpect(jsonPath("$.citas").isNotEmpty());
    }

    @Test
    @DisplayName("horas-extra con divisor explícito (Tenerife): el desglose lo marca para no venderlo como jornada anual")
    void horasExtraDivisorExplicito() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/horas-extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"tenerife-hosteleria","anio":2026,
                                 "salarioBaseMensual":1200,"plusesAnuales":0,"horas":5}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desglose.divisorHoras").value(1829))
                .andExpect(jsonPath("$.desglose.esDivisorExplicito").value(true));
    }

    @Test
    @DisplayName("horas-extra sin datos publicados (Teruel 2024+ pendiente... Ceuta sin jornada) → 422")
    void horasExtraDatosPendientes() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/horas-extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"ceuta-hosteleria","anio":2026,
                                 "salarioBaseMensual":1200,"plusesAnuales":0,"horas":5}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("horas-extra con convenio inexistente → 404")
    void horasExtraConvenioNoExiste() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/horas-extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"narnia-hosteleria","anio":2026,
                                 "salarioBaseMensual":1200,"plusesAnuales":0,"horas":5}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("horas-extra con salario 0 → 400 (validación de entrada)")
    void horasExtraSalarioInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/horas-extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","anio":2026,
                                 "salarioBaseMensual":0,"plusesAnuales":0,"horas":5}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("JSON malformado → 400 en formato RFC 7807 (mismo contrato que el resto de errores)")
    void jsonMalformado() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/horas-extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/v1/calculo/salario-base: cocinero de Madrid en 2026 → 1.250,91 (ultraactividad)")
    void salarioBaseMadrid() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","fecha":"2026-07-08",
                                 "dimensiones":{"tabla":"general","nivel":"III","claseEmpresa":"B"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importe").value(1250.91))
                .andExpect(jsonPath("$.unidad").value("EUR/mes"));
    }

    @Test
    @DisplayName("salario-base sin tabla aplicable → 404")
    void salarioBaseSinTabla() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","fecha":"2026-07-08",
                                 "dimensiones":{"tabla":"general","nivel":"XX","claseEmpresa":"B"}}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("salario-base sin dimensiones → 400")
    void salarioBaseSinDimensiones() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","fecha":"2026-07-08","dimensiones":{}}"""))
                .andExpect(status().isBadRequest());
    }
}
