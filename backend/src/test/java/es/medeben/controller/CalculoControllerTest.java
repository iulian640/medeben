package es.medeben.controller;

import es.medeben.config.SecurityConfig;
import es.medeben.repository.ConvenioCatalog;
import es.medeben.repository.HechosCatalog;
import es.medeben.service.CalculoConvenioService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CalculoController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        ConvenioCatalog.class, HechosCatalog.class,
        CalculoConvenioService.class, TablaSalarialService.class,
        es.medeben.service.SmiService.class})
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
    @DisplayName("cocinero de Madrid (1.250,91) alcanza el SMI: bajoSmi=false")
    void salarioBaseAlcanzaSmi() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","fecha":"2026-07-08",
                                 "dimensiones":{"tabla":"general","nivel":"III","claseEmpresa":"B"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bajoSmi").value(false))
                .andExpect(jsonPath("$.smiMensual").value(1221.00))
                // Alcanza el SMI: no hay suelo que enseñar por encima de la tabla,
                // y 1.250,91 > 1.221 tampoco necesita aclaración anual.
                .andExpect(jsonPath("$.minimoLegal").isEmpty())
                .andExpect(jsonPath("$.comparativaSmi").isEmpty());
    }

    @Test
    @DisplayName("cómputo ANUAL, no mensual: Pontevedra nivel 9 (1.166,15 × 15 pagas) SÍ alcanza el SMI → sin aviso")
    void salarioBaseAnualConQuincePagas() throws Exception {
        // El caso que parecía ilegal (1.166,15 < 1.221 SMI mensual) NO lo es:
        // Pontevedra paga 15 mensualidades, 1.166,15 × 15 = 17.492 ≥ 17.094.
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"pontevedra-hosteleria","fecha":"2026-07-08",
                                 "dimensiones":{"nivel":"9"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importe").value(1166.15))
                .andExpect(jsonPath("$.bajoSmi").value(false))
                // PERO parece ilegal (1.166,15 < 1.221 del titular): viajan los
                // números de la cuenta anual para que la UI se adelante a la duda.
                .andExpect(jsonPath("$.comparativaSmi.mensualidades").value(15))
                .andExpect(jsonPath("$.comparativaSmi.anualConvenio").value(17492.25))
                .andExpect(jsonPath("$.comparativaSmi.smiAnual").value(17094.00))
                .andExpect(jsonPath("$.citas[?(@.texto =~ /.*Salario Mínimo.*/)]").exists());
    }

    @Test
    @DisplayName("bajo SMI REAL: limpieza Madrid nivel V-C (1.086,31 × 14) < SMI anual → avisa con cita")
    void salarioBaseBajoSmiAvisa() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","fecha":"2026-07-08",
                                 "dimensiones":{"tabla":"general","nivel":"V","claseEmpresa":"C"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importe").value(1086.31))
                .andExpect(jsonPath("$.bajoSmi").value(true))
                .andExpect(jsonPath("$.smiMensual").value(1221.00))
                // Madrid paga 14 mensualidades: 17.094 / 14 = 1.221,00 justos.
                .andExpect(jsonPath("$.minimoLegal").value(1221.00))
                .andExpect(jsonPath("$.comparativaSmi").isEmpty())
                .andExpect(jsonPath("$.citas[?(@.texto =~ /.*Salario Mínimo.*/)]").exists());
    }

    @Test
    @DisplayName("EUR/año bajo SMI (review CRITICAL): Cuenca nivel I (16.175 €/año) < SMI anual → avisa")
    void salarioBaseAnualBajoSmi() throws Exception {
        // Cuenca publica en EUR/año; 16.175,05 < 17.094 (SMI anual 2026) → bajo SMI.
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"cuenca-hosteleria","fecha":"2026-07-08",
                                 "dimensiones":{"nivel":"I","grupoEstablecimiento":"A"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidad").value("EUR/año"))
                .andExpect(jsonPath("$.bajoSmi").value(true))
                // En EUR/año el suelo va en la misma unidad: el SMI anual entero.
                .andExpect(jsonPath("$.minimoLegal").value(17094.00))
                .andExpect(jsonPath("$.citas[?(@.texto =~ /.*Salario Mínimo.*/)]").exists());
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
