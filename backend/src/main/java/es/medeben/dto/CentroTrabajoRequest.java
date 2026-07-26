package es.medeben.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Declaración del centro de trabajo. El radio lo pone el servidor, no viaja aquí (síntesis §7). */
public record CentroTrabajoRequest(
        @NotNull(message = "no puede faltar") @DecimalMin(value = "-90.0", message = "fuera de rango")
        @DecimalMax(value = "90.0", message = "fuera de rango") BigDecimal latitud,
        @NotNull(message = "no puede faltar") @DecimalMin(value = "-180.0", message = "fuera de rango")
        @DecimalMax(value = "180.0", message = "fuera de rango") BigDecimal longitud,
        @Size(max = 60, message = "no puede pasar de 60 caracteres") @SinNul String alias
) {

    /** Ninguna coordenada en ningún log, jamás. */
    @Override
    public String toString() {
        return "CentroTrabajoRequest[latitud=***, longitud=***, alias=" + alias + "]";
    }
}
