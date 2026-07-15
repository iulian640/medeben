package es.medeben.ratelimit;

import es.medeben.config.RelojConfig;
import es.medeben.config.SecurityConfig;
import es.medeben.controller.AuthController;
import es.medeben.controller.GlobalExceptionHandler;
import es.medeben.service.AuthService;
import es.medeben.service.CredencialesInvalidasException;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aunque {@code confiar-en-proxy=true}, la cabecera {@code X-Forwarded-For}
 * solo se acepta si la petición llega DESDE un proxy de confianza
 * ({@code proxies-de-confianza}, lista de CIDRs). Hallazgo del second review:
 * sin esta comprobación, cualquier despliegue futuro que exponga el backend
 * directamente (sin nginx delante) dejaría a cualquier cliente falsear su IP
 * con un XFF forjado y saltarse el límite anti-fuerza-bruta.
 *
 * <p>En MockMvc el {@code remoteAddr} es siempre {@code 127.0.0.1}: para
 * simular un peer NO confiable basta con configurar una lista que no incluya
 * loopback.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfig.class, RelojConfig.class})
@TestPropertySource(properties = {
        "medeben.rate-limit.habilitado=true",
        "medeben.rate-limit.confiar-en-proxy=true",
        // El peer de MockMvc (127.0.0.1) NO está en la lista: XFF no es fiable.
        "medeben.rate-limit.proxies-de-confianza=203.0.113.0/24",
        "medeben.rate-limit.auth.capacidad=2",
        "medeben.rate-limit.auth.recarga-por-minuto=2"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitFilterProxyPeerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private void loginMalo(String xff, int esperado) throws Exception {
        // doThrow (no when().thenThrow()): re-grabar con thenThrow dispararía el
        // stub anterior al ejecutar authService.login() dentro de when().
        doThrow(new CredencialesInvalidasException()).when(authService).login(anyString(), anyString());
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", xff)
                        .contentType("application/json")
                        .content("{\"email\":\"a@b.dev\",\"password\":\"malapass-123\"}"))
                .andExpect(status().is(esperado));
    }

    @Test
    @DisplayName("peer fuera de proxies-de-confianza: el XFF se IGNORA y la clave es remoteAddr")
    void peerNoConfiableIgnoraElXff() throws Exception {
        // El "cliente" (peer 127.0.0.1, no confiable) rota el último valor del
        // XFF en cada petición, como haría un atacante con el backend expuesto.
        // Si el filtro se lo creyera, cada petición estrenaría cubeta y jamás
        // llegaría el 429; ignorándolo, las tres caen en la cubeta de 127.0.0.1.
        loginMalo("6.6.6.1", 401); // capacidad 2: 1ª pasa
        loginMalo("6.6.6.2", 401); // 2ª pasa
        loginMalo("6.6.6.3", 429); // 3ª: cubeta de remoteAddr agotada
    }
}
