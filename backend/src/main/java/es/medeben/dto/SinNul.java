package es.medeben.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rechaza el carácter NUL (U+0000) en un campo de texto. PostgreSQL no lo
 * admite en ningún texto —columna o JSONB—, así que sin esta validación un
 * NUL pegado desde otra app acababa en un 500 en vez de en un 400 claro
 * (issue #232). Va en TODO campo de texto libre de los request DTOs; los
 * campos con {@code @Pattern} de lista blanca (horas HH:mm) ya lo rechazan
 * solos y no la necesitan. El resto de caracteres raros (emoji, RTL, HTML)
 * se guardan como texto plano sin problema: solo el NUL rompe.
 */
@Documented
@Constraint(validatedBy = SinNul.Validador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SinNul {

    // El mensaje nunca ecoa el valor recibido: podría acabar en logs y respuestas.
    String message() default "no puede contener el carácter nulo (U+0000)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<SinNul, String> {

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            // null es válido aquí: de exigir presencia se encargan @NotNull/@NotBlank.
            return valor == null || valor.indexOf('\0') < 0;
        }
    }
}
