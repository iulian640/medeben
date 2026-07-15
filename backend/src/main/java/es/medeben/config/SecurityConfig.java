package es.medeben.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.medeben.ratelimit.RateLimitFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración de seguridad base.
 *
 * <p>Públicos: el health check, los datos de convenios (boletines oficiales),
 * los cálculos anónimos y el alta/login. Todo lo demás exige un JWT Bearer
 * (HS256, ver {@link JwtConfig}) validado por el resource server. La cadena es
 * stateless y sin CSRF (API pura de tokens, sin sesiones ni cookies), y los
 * 401 salen como ProblemDetail RFC 7807 con copy neutro, igual que el resto
 * de errores de la API.
 *
 * <p>El {@link RateLimitFilter} (si está presente en el contexto — ver
 * {@code RateLimitConfig}) se registra ANTES del filtro de autenticación
 * Bearer: así frena la fuerza bruta en {@code /auth/**} por IP sin gastar
 * CPU validando JWT, y limita el resto de {@code /api/**} aunque el token
 * no llegue a autenticar. Se inyecta como {@link ObjectProvider} para que
 * los tests de slice ({@code @WebMvcTest}) que no importan {@code RateLimitConfig}
 * sigan construyendo la cadena exactamente igual que antes, sin el filtro.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Orígenes permitidos por CORS. Contraintuición importante: al habilitar CORS,
     * Spring valida CUALQUIER petición que traiga cabecera {@code Origin} contra
     * esta lista — incluidas las SAME-ORIGIN (el navegador manda Origin en los
     * POST/PUT/DELETE aunque el destino sea el mismo host). Por eso la lista debe
     * incluir también el origen de la propia web, no solo el del APK: si no,
     * el registro/login de la PWA (mismo origen) se rechazaría con 403.
     *
     * Orígenes por defecto (cubren dev, CI y producción sin configurar nada):
     * - {@code https://localhost}: el WebView de Capacitor del APK Android (sin
     *   androidScheme propio) — el único cliente REALMENTE cross-origin.
     * - {@code https://medeben.net}: la PWA web en producción (same-origin, pero
     *   manda Origin en los POST).
     * - {@code http://localhost:4180} y {@code :5173}: la web en E2E (Playwright)
     *   y en el dev server de Vite.
     * NUNCA {@code *}: el API expone datos personales (RGPD). En prod se puede
     * afinar (quitar los localhost de dev) con MEDEBEN_SEGURIDAD_CORS_ORIGENES;
     * dejarlos es inocuo (Bearer en cabecera, sin cookies: allowCredentials=false).
     */
    private static final String ORIGENES_POR_DEFECTO =
            "https://localhost,https://medeben.net,http://localhost:4180,http://localhost:5173";

    private final List<String> origenesCors;

    public SecurityConfig(
            @Value("${medeben.seguridad.cors-origenes:" + ORIGENES_POR_DEFECTO + "}") List<String> origenesCors) {
        this.origenesCors = origenesCors;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(origenesCors);
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // El credencial viaja como Bearer en la cabecera, no en cookies: no hace
        // falta allowCredentials (más seguro, y así jamás combina con orígenes '*').
        configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuracion.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuracion);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                                     ObjectProvider<RateLimitFilter> rateLimitFilterProvider)
            throws Exception {
        // El mismo 401 neutro para "sin token" y para "token inválido/caducado":
        // va cableado en los DOS sitios porque el filtro Bearer usa su propio
        // entry point (no el de exceptionHandling) cuando el token no valida.
        AuthenticationEntryPoint entryPoint = new ProblemDetailEntryPoint(objectMapper);
        http
                // CORS ANTES que nada: el preflight OPTIONS debe resolverse sin
                // exigir autenticación (si no, el APK ni llega a la petición real).
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/actuator/health").permitAll()
                        // Datos de convenios (boletines oficiales) y cálculos anónimos:
                        // públicos por diseño, sin datos personales de por medio.
                        .requestMatchers(HttpMethod.GET, "/api/v1/provincias", "/api/v1/puestos", "/api/v1/convenios/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/calculo/**").permitAll()
                        // refresh y logout van con el refresh token en el body, no con
                        // el access (que puede estar ya caducado): públicos como el login.
                        // verifica-email y reenvia-verificacion: quien llega del enlace
                        // del correo no tiene sesión — públicos por necesidad.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/registro", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout",
                                "/api/v1/auth/verifica-email", "/api/v1/auth/reenvia-verificacion").permitAll()
                        .anyRequest().authenticated())
                // Los endpoints autenticados validan un JWT Bearer (HS256, JwtConfig)
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                // API stateless: sin token no hay redirect a login, hay un 401
                // RFC 7807 (mismo shape que el GlobalExceptionHandler).
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));

        RateLimitFilter rateLimitFilter = rateLimitFilterProvider.getIfAvailable();
        if (rateLimitFilter != null) {
            http.addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }
}
