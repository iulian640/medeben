package es.medeben.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Ajustes de (de)serialización JSON de la API.
 *
 * <p>El deserializador de {@link LocalDate} que trae Jackson es indulgente:
 * acepta un timestamp completo ("2026-07-08T22:00:00.000Z" — lo que produce
 * {@code new Date().toISOString()} en JavaScript) y se queda con el día EN
 * UTC sin avisar. Para alguien en España en horario de verano (UTC+2), las
 * 00:00 del día 9 son las 22:00 del día 8 en UTC: el fichaje quedaría
 * registrado un día antes del real, en silencio (issue #232). Aquí se
 * sustituye por uno estricto en TODOS los campos LocalDate del body: solo
 * yyyy-MM-dd, y cualquier otra cosa es un 400 con mensaje claro.
 */
@Configuration
public class JacksonConfig {

    /** El motivo que ve el usuario; el GlobalExceptionHandler le antepone el campo. */
    public static final String MENSAJE_FECHA_ESTRICTA =
            "se espera una fecha en formato yyyy-MM-dd, sin hora ni zona horaria";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer fechasIsoEstrictas() {
        return builder -> builder.deserializerByType(LocalDate.class, new FechaIsoEstrictaDeserializer());
    }

    /** Solo yyyy-MM-dd. La serialización (respuestas) no cambia: ya sale así. */
    static final class FechaIsoEstrictaDeserializer extends JsonDeserializer<LocalDate> {

        private static final Pattern SOLO_FECHA = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

        @Override
        public LocalDate deserialize(JsonParser parser, DeserializationContext contexto) throws IOException {
            String crudo = parser.getValueAsString();
            if (crudo == null || !SOLO_FECHA.matcher(crudo).matches()) {
                throw fechaInvalida(parser);
            }
            try {
                return LocalDate.parse(crudo);
            } catch (DateTimeParseException e) {
                // Forma correcta pero fecha imposible (2026-13-45).
                throw fechaInvalida(parser);
            }
        }

        /**
         * Ni el mensaje ni el "value" de la excepción llevan el valor recibido:
         * acabaría ecoado en respuestas y logs (mismo criterio que MesPath).
         * El GlobalExceptionHandler convierte esto en el 400 RFC 7807.
         */
        private static InvalidFormatException fechaInvalida(JsonParser parser) {
            return InvalidFormatException.from(parser, MENSAJE_FECHA_ESTRICTA, null, LocalDate.class);
        }
    }
}
