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
        @NotBlank(message = "no puede faltar")
        @Size(max = 40, message = "no puede pasar de 40 caracteres") @SinNul String convenioId,
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
        BigDecimal horas,
        // Dimensiones OPCIONALES (issue #231): mismo blindaje que SalarioBaseRequest
        // — tope de entradas, de clave y de valor, y @SinNul en ambos (issue #232).
        @Size(max = 10, message = "máximo 10 dimensiones")
        Map<@NotBlank(message = "no puede faltar") @Size(max = 40, message = "no puede pasar de 40 caracteres") @SinNul String,
                @NotBlank(message = "no puede faltar") @Size(max = 400, message = "no puede pasar de 400 caracteres") @SinNul String> dimensiones
) {

    public BigDecimal plusesONada() {
        return plusesAnuales == null ? BigDecimal.ZERO : plusesAnuales;
    }

    public Map<String, String> dimensionesONada() {
        return dimensiones == null ? Map.of() : dimensiones;
    }
}
