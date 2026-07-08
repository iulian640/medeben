package es.tedeben.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CalculoHorasExtraRequest(
        @NotBlank String convenioId,
        @NotNull @Min(2000) @Max(2100) Integer anio,
        @NotNull @Positive BigDecimal salarioBaseMensual,
        @PositiveOrZero BigDecimal plusesAnuales,
        @NotNull @PositiveOrZero BigDecimal horas
) {

    public BigDecimal plusesONada() {
        return plusesAnuales == null ? BigDecimal.ZERO : plusesAnuales;
    }
}
