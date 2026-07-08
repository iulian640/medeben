package es.tedeben.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Base security configuration.
 *
 * <p>Public endpoints: the app health check and the actuator health probe.
 * Everything else requires authentication. JWT auth will be plugged in here
 * later (D13.4: email + password, Spring Security + JWT) — for now the chain
 * is stateless with CSRF disabled, ready for a token filter.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
                // Stateless API: unauthenticated requests get a plain 401
                // (no redirect to a login page).
                // Los endpoints autenticados validan un JWT Bearer (HS256, JwtConfig)
                .oauth2ResourceServer(oauth -> oauth.jwt(org.springframework.security.config.Customizer.withDefaults()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
