package es.medeben.ratelimit;

import es.medeben.config.RelojConfig;
import es.medeben.config.SecurityConfig;
import es.medeben.controller.AuthController;
import es.medeben.controller.GlobalExceptionHandler;
import es.medeben.service.AuthService;
import es.medeben.service.EmailYaRegistradoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Presupuesto GLOBAL de {@code /auth/registro} (second review): el bucket por
 * IP confina a un atacante con UNA IP, pero la enumeración de cuentas real se
 * hace distribuida — mil IPs probando un email cada una ni rozan el límite por
 * IP. Este bucket único (compartido por TODAS las IPs) acota el total de
 * intentos de registro del sistema entero. Trade-off asumido (decisión de
 * producto): un atacante puede agotarlo y provocar 429 a registros legítimos
 * durante un rato; para el volumen real de la app es preferible a la
 * enumeración ilimitada.
 *
 * <p>El peer de MockMvc es {@code 127.0.0.1} (proxy de confianza por defecto),
 * así que rotar el último valor de {@code X-Forwarded-For} simula IPs de
 * cliente distintas.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfig.class, RelojConfig.class})
@TestPropertySource(properties = {
        "medeben.rate-limit.habilitado=true",
        "medeben.rate-limit.confiar-en-proxy=true",
        // Por IP holgado (5) y global estrecho (2): así el 429 del test solo
        // puede venir del bucket global, nunca del de IP.
        "medeben.rate-limit.registro.capacidad=5",
        "medeben.rate-limit.registro.recarga-por-minuto=2",
        "medeben.rate-limit.registro-global.capacidad=2",
        "medeben.rate-limit.registro-global.recarga-por-minuto=1",
        "medeben.rate-limit.auth.capacidad=50",
        "medeben.rate-limit.auth.recarga-por-minuto=50"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitFilterRegistroGlobalTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private void registroDesde(String ip, int esperado) throws Exception {
        // doThrow (no when().thenThrow()): re-grabar con thenThrow dispararía el
        // stub anterior al ejecutar authService.registra() dentro de when().
        doThrow(new EmailYaRegistradoException()).when(authService).registra(anyString(), anyString());
        mockMvc.perform(post("/api/v1/auth/registro")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"una-contraseña-larga"}"""))
                .andExpect(status().is(esperado));
    }

    @Test
    @DisplayName("IPs distintas comparten el presupuesto GLOBAL de registro: la 3ª (capacidad 2) -> 429")
    void ipsDistintasCompartenElPresupuestoGlobal() throws Exception {
        // Enumeración distribuida: cada petición llega de una IP nueva, así que
        // ninguna cubeta por IP pasa de 1 consumo. Sin bucket global las tres
        // serían 409; con él, la 3ª agota el total del sistema.
        registroDesde("20.0.0.1", 409);
        registroDesde("20.0.0.2", 409);
        registroDesde("20.0.0.3", 429);
    }

    @Test
    @DisplayName("el 429 del bucket global lleva Retry-After, como el resto de límites")
    void elGlobalTambienDevuelveRetryAfter() throws Exception {
        registroDesde("30.0.0.1", 409);
        registroDesde("30.0.0.2", 409);
        mockMvc.perform(post("/api/v1/auth/registro")
                        .header("X-Forwarded-For", "30.0.0.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"una-contraseña-larga"}"""))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("el bucket global es solo de registro: no estrangula al login")
    void elGlobalNoTocaElLogin() throws Exception {
        registroDesde("40.0.0.1", 409);
        registroDesde("40.0.0.2", 409);
        registroDesde("40.0.0.3", 429);

        // El login ni conoce ese bucket: sigue con el presupuesto de auth.
        doThrow(new es.medeben.service.CredencialesInvalidasException())
                .when(authService).login(anyString(), anyString());
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "40.0.0.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"da-igual-123"}"""))
                .andExpect(status().isUnauthorized());
    }
}
