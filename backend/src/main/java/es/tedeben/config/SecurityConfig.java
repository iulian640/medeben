package es.tedeben.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad base.
 *
 * <p>Públicos: el health check, los datos de convenios (boletines oficiales),
 * los cálculos anónimos y el alta/login. Todo lo demás exige un JWT Bearer
 * (HS256, ver {@link JwtConfig}) validado por el resource server. La cadena es
 * stateless y sin CSRF (API pura de tokens, sin sesiones ni cookies), y los
 * 401 salen como ProblemDetail RFC 7807 con copy neutro, igual que el resto
 * de errores de la API.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
            throws Exception {
        // El mismo 401 neutro para "sin token" y para "token inválido/caducado":
        // va cableado en los DOS sitios porque el filtro Bearer usa su propio
        // entry point (no el de exceptionHandling) cuando el token no valida.
        AuthenticationEntryPoint entryPoint = new ProblemDetailEntryPoint(objectMapper);
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/actuator/health").permitAll()
                        // Datos de convenios (boletines oficiales) y cálculos anónimos:
                        // públicos por diseño, sin datos personales de por medio.
                        .requestMatchers(HttpMethod.GET, "/api/v1/provincias", "/api/v1/puestos", "/api/v1/convenios/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/calculo/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/registro", "/api/v1/auth/login").permitAll()
                        .anyRequest().authenticated())
                // Los endpoints autenticados validan un JWT Bearer (HS256, JwtConfig)
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                // API stateless: sin token no hay redirect a login, hay un 401
                // RFC 7807 (mismo shape que el GlobalExceptionHandler).
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));
        return http.build();
    }
}
