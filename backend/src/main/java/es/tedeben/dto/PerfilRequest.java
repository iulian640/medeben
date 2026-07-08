package es.tedeben.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record PerfilRequest(
        @NotBlank @Size(max = 60) String provincia,
        @NotBlank @Size(max = 30) String subsector,
        @Size(max = 40) String puestoId,
        @Size(max = 10) Map<String, String> dimensiones,
        @Positive @Digits(integer = 7, fraction = 2) BigDecimal salarioBaseMensual,
        @PositiveOrZero @Digits(integer = 7, fraction = 2) BigDecimal plusesAnuales
) {
}
