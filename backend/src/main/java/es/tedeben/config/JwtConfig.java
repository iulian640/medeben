package es.tedeben.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Claves JWT (HS256 simétrico). El secreto viene de configuración:
 * en producción SIEMPRE por variable de entorno (TEDEBEN_SEGURIDAD_JWT_SECRETO),
 * nunca hardcodeado; los perfiles dev/local/test llevan secretos de juguete
 * claramente marcados. Mínimo 32 bytes (HS256).
 */
@Configuration
public class JwtConfig {

    private static final int MIN_BYTES_SECRETO = 32;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(@Value("${tedeben.seguridad.jwt.secreto}") String secreto) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave(secreto)));
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${tedeben.seguridad.jwt.secreto}") String secreto) {
        return NimbusJwtDecoder.withSecretKey(clave(secreto))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static SecretKeySpec clave(String secreto) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_BYTES_SECRETO) {
            throw new IllegalStateException(
                    "El secreto JWT debe tener al menos " + MIN_BYTES_SECRETO + " bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
