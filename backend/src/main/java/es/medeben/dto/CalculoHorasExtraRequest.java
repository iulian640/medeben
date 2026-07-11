package es.medeben.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Endpoint ANÓNIMO (calculo/horas-extra es permitAll). Las {@code dimensiones}
 * son OPCIONALES: solo las necesitan los convenios cuya jornada o pagas van por
 * dimensión (la colectiva las publica por provincia, issue #231). Cada clave y
 * cada valor van acotados además del número de entradas, igual que en
 * {@link SalarioBaseRequest}: sin el tope individual, 10 entradas podían colar
 * megas de texto en un endpoint sin autenticar (DoS de memoria/parseo).
 */
public record CalculoHorasExtraRequest(
        @NotBlank @Size(max = 40) String convenioId,
        @NotNull @Min(2000) @Max(2100) Integer anio,
        @NotNull @Positive @Digits(integer = 7, fraction = 2) BigDecimal salarioBaseMensual,
        @PositiveOrZero @Digits(integer = 7, fraction = 2) BigDecimal plusesAnuales,
        @NotNull @PositiveOrZero @Digits(integer = 5, fraction = 2) BigDecimal horas,
        @Size(max = 10) Map<@NotBlank @Size(max = 40) String, @NotBlank @Size(max = 400) String> dimensiones
) {

    public BigDecimal plusesONada() {
        return plusesAnuales == null ? BigDecimal.ZERO : plusesAnuales;
    }

    public Map<String, String> dimensionesONada() {
        return dimensiones == null ? Map.of() : dimensiones;
    }
}
