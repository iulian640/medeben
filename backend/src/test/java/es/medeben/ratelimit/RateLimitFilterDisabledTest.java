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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Con {@code medeben.rate-limit.habilitado=false} el filtro no debe limitar
 * nada, aunque el presupuesto configurado sea minúsculo. Es el mecanismo que
 * usa el perfil {@code test} (ver application-test.yml) para no afectar al
 * resto de la suite.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfig.class, RelojConfig.class})
@TestPropertySource(properties = {
        "medeben.rate-limit.habilitado=false",
        "medeben.rate-limit.auth.capacidad=1",
        "medeben.rate-limit.auth.recarga-por-minuto=1"
})
class RateLimitFilterDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("con habilitado=false, ninguna petición se limita aunque se supere el presupuesto configurado")
    void conHabilitadoFalseNingunaPeticionSeLimita() throws Exception {
        when(authService.login(anyString(), anyString())).thenThrow(new CredencialesInvalidasException());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"trabajador@example.com","password":"mala"}"""))
                    .andExpect(status().isUnauthorized());
        }
    }
}
