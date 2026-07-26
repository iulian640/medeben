package es.medeben.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Coordenadas para adjuntar a un fichaje (síntesis §5, contrato §Backend
 * regla 7 de rangos). {@code precisionMetros} tope en 5000: por encima no se
 * guarda basura (síntesis §6, tabla de estados degradados).
 */
public record UbicacionRequest(
        @NotNull(message = "no puede faltar") @DecimalMin(value = "-90.0", message = "fuera de rango")
        @DecimalMax(value = "90.0", message = "fuera de rango") BigDecimal latitud,
        @NotNull(message = "no puede faltar") @DecimalMin(value = "-180.0", message = "fuera de rango")
        @DecimalMax(value = "180.0", message = "fuera de rango") BigDecimal longitud,
        @NotNull(message = "no puede faltar") @Min(value = 1, message = "fuera de rango")
        @Max(value = 5000, message = "fuera de rango") Integer precisionMetros
) {

    /** Ninguna coordenada en ningún log, jamás (mismo patrón que ApunteRequest/motivo). */
    @Override
    public String toString() {
        return "UbicacionRequest[latitud=***, longitud=***, precisionMetros=" + precisionMetros + "]";
    }
}
