package es.medeben.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/** Claves JWT de TEST (secreto fijo, solo para tests). */
final class JwtTestSupport {

    static final Duration DURACION = Duration.ofHours(24);

    private static final String SECRETO_TEST =
            "secreto-de-test-para-jwt-de-32-bytes-o-mas-no-usar-en-produccion";

    private JwtTestSupport() {
    }

    record Claves(JwtEncoder encoder, JwtDecoder decoder) {
    }

    static Claves claves() {
        return claves(Clock.systemUTC());
    }

    /**
     * Claves cuyo DECODER valida la caducidad contra {@code reloj}, no contra la
     * hora real. Necesario cuando el test emite el token con un Clock FIJO (p. ej.
     * {@code AuthServiceTest}): si el decoder usara el reloj del sistema, un token
     * minteado a una hora fija del pasado caducaría en cuanto la hora real pasara
     * su {@code exp} —el build reventaba solo por el paso del tiempo—. Con el mismo
     * reloj fijo, la validación es determinista.
     */
    static Claves claves(Clock reloj) {
        SecretKeySpec clave = new SecretKeySpec(
                SECRETO_TEST.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(clave)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator validadorCaducidad = new JwtTimestampValidator();
        validadorCaducidad.setClock(reloj);
        decoder.setJwtValidator(validadorCaducidad);
        return new Claves(encoder, decoder);
    }
}
