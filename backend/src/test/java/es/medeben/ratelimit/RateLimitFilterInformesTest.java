package es.medeben.ratelimit;

import es.medeben.config.RelojConfig;
import es.medeben.config.SecurityConfig;
import es.medeben.controller.GlobalExceptionHandler;
import es.medeben.controller.InformeController;
import es.medeben.service.InformeMensualService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El presupuesto PROPIO de {@code /api/v1/informes/**} (hallazgo del security
 * review): generar un PDF es mucho más caro que un GET normal, así que con el
 * presupuesto genérico de la API una sola cuenta podía sostener decenas de
 * generaciones por minuto (amplificación de CPU). Aquí se prueba que el cubo
 * de informes es independiente y más estrecho que el del resto de la API.
 */
@WebMvcTest(InformeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfig.class, RelojConfig.class})
@TestPropertySource(properties = {
        "medeben.rate-limit.habilitado=true",
        "medeben.rate-limit.confiar-en-proxy=false",
        "medeben.rate-limit.informes.capacidad=2",
        "medeben.rate-limit.informes.recarga-por-minuto=1",
        "medeben.rate-limit.api.capacidad=50",
        "medeben.rate-limit.api.recarga-por-minuto=50"
})
// Cada test agota el presupuesto de informes; contexto nuevo por test para
// no heredar cubetas agotadas.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitFilterInformesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InformeMensualService informes;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("los informes tienen su propio cubo, más estrecho que el de la API")
    void presupuestoPropioDeInformes() throws Exception {
        // El filtro corre ANTES de la autenticación: dos intentos (aquí 401)
        // consumen el cubo de informes de esta IP; el tercero ya es 429 aunque
        // el presupuesto genérico de la API (50) esté intacto.
        mockMvc.perform(get("/api/v1/informes/mes/2026-07")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/informes/mes/2026-07")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/informes/mes/2026-07")).andExpect(status().isTooManyRequests());

        // El resto de la API sigue con su presupuesto: 401 (sin token), nunca 429.
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }
}
