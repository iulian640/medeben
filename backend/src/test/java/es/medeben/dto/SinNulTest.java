package es.medeben.dto;

import es.medeben.domain.fichaje.TipoApunte;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL rechaza el carácter NUL (U+0000) en cualquier texto —columna o
 * JSONB—, así que sin validarlo antes un NUL acababa en 500 (issue #232).
 * El cierre es uniforme: TODO campo de texto libre de la API lo rechaza con
 * el mismo mensaje. Los campos con {@code @Pattern} de lista blanca (horas
 * HH:mm) ya lo rechazaban solos y no necesitan la anotación.
 */
@DisplayName("@SinNul — ningún campo de texto de la API admite el carácter NUL")
class SinNulTest {

    private static final String CON_NUL = "texto con nul \0 dentro";
    private static final String MENSAJE = "no puede contener el carácter nulo (U+0000)";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void arrancaValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cierraValidador() {
        factory.close();
    }

    private static <T> void asertaRechazado(T dto) {
        Set<ConstraintViolation<T>> violaciones = validator.validate(dto);
        assertThat(violaciones)
                .as("el NUL debe rechazarse en " + dto.getClass().getSimpleName())
                .anyMatch(v -> MENSAJE.equals(v.getMessage()));
    }

    @Test
    @DisplayName("motivo de una ausencia")
    void motivoDeAusencia() {
        asertaRechazado(new ApunteRequest(LocalDate.of(2026, 7, 8), TipoApunte.AUSENCIA, null, CON_NUL, false));
    }

    @Test
    @DisplayName("email y password del registro")
    void registro() {
        asertaRechazado(new RegistroRequest(CON_NUL + "@example.com", "una-password-larga"));
        asertaRechazado(new RegistroRequest("valido@example.com", "pass\0word-larga"));
    }

    @Test
    @DisplayName("email y password del login")
    void login() {
        asertaRechazado(new LoginRequest(CON_NUL + "@example.com", "loquesea"));
        asertaRechazado(new LoginRequest("valido@example.com", CON_NUL));
    }

    @Test
    @DisplayName("perfil: provincia, subsector, puesto y dimensiones (claves y valores, van a JSONB)")
    void perfil() {
        asertaRechazado(new PerfilRequest(CON_NUL, "hosteleria", null, Map.of(), null, null));
        asertaRechazado(new PerfilRequest("Madrid", CON_NUL, null, Map.of(), null, null));
        asertaRechazado(new PerfilRequest("Madrid", "hosteleria", CON_NUL, Map.of(), null, null));
        asertaRechazado(new PerfilRequest("Madrid", "hosteleria", null, Map.of(CON_NUL, "III"), null, null));
        asertaRechazado(new PerfilRequest("Madrid", "hosteleria", null, Map.of("nivel", CON_NUL), null, null));
    }

    @Test
    @DisplayName("cálculo anónimo: convenioId y dimensiones")
    void calculoAnonimo() {
        asertaRechazado(new SalarioBaseRequest(CON_NUL, LocalDate.of(2026, 7, 8), Map.of("nivel", "III")));
        asertaRechazado(new SalarioBaseRequest("madrid-hosteleria", LocalDate.of(2026, 7, 8), Map.of("nivel", CON_NUL)));
        // horas-extra: convenioId, y la dimensión OPCIONAL (issue #231) también con @SinNul.
        asertaRechazado(new CalculoHorasExtraRequest(CON_NUL, 2026, BigDecimal.ONE, null, BigDecimal.ONE, null));
        asertaRechazado(new CalculoHorasExtraRequest(
                "estatal-restauracion-colectiva", 2026, BigDecimal.ONE, null, BigDecimal.ONE,
                Map.of("provincia", CON_NUL)));
    }

    @Test
    @DisplayName("password del borrado de cuenta y refresh token")
    void cuentaYSesiones() {
        asertaRechazado(new BorradoCuentaRequest(CON_NUL));
        asertaRechazado(new RefreshRequest(CON_NUL));
    }

    @Test
    @DisplayName("el texto normal (emoji, saltos de línea, tildes) sigue pasando")
    void textoNormalPasa() {
        var peticion = new ApunteRequest(LocalDate.of(2026, 7, 8), TipoApunte.AUSENCIA, null,
                "migraña con aura 🤕\nbaja médica", false);

        assertThat(validator.validate(peticion)).isEmpty();
    }
}
