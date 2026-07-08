package es.tedeben.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record PerfilRequest(
        @NotBlank @Size(max = 60) String provincia,
        @NotBlank @Size(max = 30) String subsector,
        @Size(max = 40) String puestoId,
        // Además del nº de entradas, cada clave y cada valor van acotados:
        // sin tope individual, 10 entradas podían colar megas de texto. El
        // valor real más largo del catálogo tiene 325 caracteres (dimensión
        // "categoría" de estatal-restauracion-colectiva), de ahí el 400.
        @Size(max = 10) Map<@NotNull @Size(max = 40) String, @NotNull @Size(max = 400) String> dimensiones,
        @Positive @Digits(integer = 7, fraction = 2) BigDecimal salarioBaseMensual,
        @PositiveOrZero @Digits(integer = 7, fraction = 2) BigDecimal plusesAnuales
) {
}
