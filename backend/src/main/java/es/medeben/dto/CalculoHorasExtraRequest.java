package es.medeben.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CalculoHorasExtraRequest(
        @NotBlank(message = "no puede faltar") @SinNul String convenioId,
        @NotNull(message = "no puede faltar")
        @Min(value = 2000, message = "fuera de rango (2000-2100)")
        @Max(value = 2100, message = "fuera de rango (2000-2100)") Integer anio,
        @NotNull(message = "no puede faltar") @Positive(message = "debe ser mayor que 0")
        @Digits(integer = 7, fraction = 2, message = "demasiados dígitos (máximo 7 enteros y 2 decimales)")
        BigDecimal salarioBaseMensual,
        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 7, fraction = 2, message = "demasiados dígitos (máximo 7 enteros y 2 decimales)")
        BigDecimal plusesAnuales,
        @NotNull(message = "no puede faltar") @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 5, fraction = 2, message = "demasiados dígitos (máximo 5 enteros y 2 decimales)")
        BigDecimal horas
) {

    public BigDecimal plusesONada() {
        return plusesAnuales == null ? BigDecimal.ZERO : plusesAnuales;
    }
}
