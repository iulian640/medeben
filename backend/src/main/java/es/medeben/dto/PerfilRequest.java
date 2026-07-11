package es.medeben.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record PerfilRequest(
        @NotBlank(message = "no puede faltar")
        @Size(max = 60, message = "no puede pasar de 60 caracteres") @SinNul String provincia,
        @NotBlank(message = "no puede faltar")
        @Size(max = 30, message = "no puede pasar de 30 caracteres") @SinNul String subsector,
        @Size(max = 40, message = "no puede pasar de 40 caracteres") @SinNul String puestoId,
        // Además del nº de entradas, cada clave y cada valor van acotados:
        // sin tope individual, 10 entradas podían colar megas de texto. El
        // valor real más largo del catálogo tiene 325 caracteres (dimensión
        // "categoría" de estatal-restauracion-colectiva), de ahí el 400.
        @Size(max = 10, message = "máximo 10 dimensiones")
        Map<@NotNull(message = "no puede faltar") @Size(max = 40, message = "no puede pasar de 40 caracteres") @SinNul String,
                @NotNull(message = "no puede faltar") @Size(max = 400, message = "no puede pasar de 400 caracteres") @SinNul String> dimensiones,
        @Positive(message = "debe ser mayor que 0")
        @Digits(integer = 7, fraction = 2, message = "demasiados dígitos (máximo 7 enteros y 2 decimales)")
        BigDecimal salarioBaseMensual,
        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 7, fraction = 2, message = "demasiados dígitos (máximo 7 enteros y 2 decimales)")
        BigDecimal plusesAnuales
) {
}
