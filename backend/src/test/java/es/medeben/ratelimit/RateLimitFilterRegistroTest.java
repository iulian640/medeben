package es.medeben.ratelimit;

import es.medeben.config.RelojConfig;
import es.medeben.config.SecurityConfig;
import es.medeben.controller.AuthController;
import es.medeben.controller.GlobalExceptionHandler;
import es.medeben.domain.usuario.Usuario;
import es.medeben.service.AuthService;
import es.medeben.service.CredencialesInvalidasException;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El bucket PROPIO de {@code /auth/registro} (hallazgo de auditoría): el 409
 * (email ya registrado) frente al 201 permite enumerar cuentas por fuerza
 * bruta. El cierre real es la verificación por email (pendiente); mientras
 * tanto, esto acota el daño — una ráfaga corta y un goteo sostenido bajo, muy
 * por debajo del genérico de auth (pensado para que una plantilla entera haga
 * login a la vez, no para registro, que es un evento raro por IP).
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfig.class, RelojConfig.class})
@TestPropertySource(properties = {
        "medeben.rate-limit.habilitado=true",
        "medeben.rate-limit.confiar-en-proxy=false",
        "medeben.rate-limit.registro.capacidad=5",
        "medeben.rate-limit.registro.recarga-por-minuto=2",
        "medeben.rate-limit.auth.capacidad=50",
        "medeben.rate-limit.auth.recarga-por-minuto=50"
})
// Cada test agota deliberadamente el presupuesto de /auth/registro; sin un
// contexto (y por tanto un RegistroCubetas) nuevo por test, el segundo test
// heredaría la cubeta ya agotada del primero.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitFilterRegistroTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("el 6º intento de registro en ráfaga (capacidad 5) -> 429 con Retry-After")
    void sextoIntentoEnRafagaDevuelve429() throws Exception {
        when(authService.registra(anyString(), anyString()))
                .thenThrow(new EmailYaRegistradoException());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(registroRequest()).andExpect(status().isConflict());
        }

        mockMvc.perform(registroRequest())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("el presupuesto de /auth/registro es propio: agotarlo no toca el del login")
    void cuboPropioDeRegistroNoTocaElDeLogin() throws Exception {
        when(authService.registra(anyString(), anyString()))
                .thenThrow(new EmailYaRegistradoException());
        when(authService.login(anyString(), anyString()))
                .thenThrow(new CredencialesInvalidasException());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(registroRequest()).andExpect(status().isConflict());
        }
        mockMvc.perform(registroRequest()).andExpect(status().isTooManyRequests());

        // El login sigue con su presupuesto (auth) intacto: 401, nunca 429.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"trabajador@example.com","password":"da-igual-123"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un registro que SÍ prospera también consume el presupuesto propio de /auth/registro")
    void registroConExitoConsumeElPresupuestoPropio() throws Exception {
        when(authService.registra(anyString(), anyString()))
                .thenReturn(new Usuario("trabajador@example.com", "hash"));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(registroRequest()).andExpect(status().isCreated());
        }

        mockMvc.perform(registroRequest()).andExpect(status().isTooManyRequests());
    }

    private MockHttpServletRequestBuilder registroRequest() {
        return post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"trabajador@example.com","password":"una-contraseña-larga"}""");
    }
}
