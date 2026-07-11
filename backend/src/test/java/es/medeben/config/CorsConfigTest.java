package es.medeben.config;

import es.medeben.controller.HealthController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS para el APK de Android (issue de despliegue medeben.net): el WebView de
 * Capacitor vive en {@code https://localhost} y llama al API en otro origen;
 * sin CORS toda petición del APK se bloquea. La PWA web va same-origin y no
 * pasa por aquí. Se comprueba que el origen del APK se permite, que un origen
 * cualquiera NO, y que el preflight OPTIONS no exige autenticación.
 */
@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class CorsConfigTest {

    private static final String ORIGEN_CAPACITOR = "https://localhost";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("el origen del WebView de Capacitor (https://localhost) recibe Access-Control-Allow-Origin")
    void origenDelApkPermitido() throws Exception {
        mockMvc.perform(get("/api/v1/health").header(HttpHeaders.ORIGIN, ORIGEN_CAPACITOR))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN_CAPACITOR));
    }

    @Test
    @DisplayName("un origen cualquiera NO recibe cabecera CORS (no es '*': el API tiene datos personales)")
    void origenAjenoRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/health").header(HttpHeaders.ORIGIN, "https://sitio-malicioso.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("el preflight OPTIONS del APK se resuelve sin autenticación (no 401)")
    void preflightNoExigeAutenticacion() throws Exception {
        // Un DELETE de cuenta con Authorization dispara preflight: debe pasar sin token.
        mockMvc.perform(options("/api/v1/cuenta")
                        .header(HttpHeaders.ORIGIN, ORIGEN_CAPACITOR)
                        .header("Access-Control-Request-Method", "DELETE")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN_CAPACITOR))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("DELETE")));
    }
}
