package es.tedeben.dto;

import es.tedeben.domain.fichaje.TipoApunte;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Un apunte del diario (D38). {@code rectificacionTardiaConfirmada} solo tiene
 * efecto sobre días ya sellados: es el "sí, sé que esto queda registrado como
 * modificación posterior al sellado" de la UI.
 */
public record ApunteRequest(
        @NotNull LocalDate fecha,
        @NotNull TipoApunte tipo,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "hora en formato HH:mm") String hora,
        @Size(max = 200) String motivo,
        boolean rectificacionTardiaConfirmada
) {

    /**
     * El toString autogenerado de un record volcaría el motivo (posible dato
     * de salud, art. 9 RGPD) en cualquier log accidental: aquí se redacta.
     */
    @Override
    public String toString() {
        return "ApunteRequest[fecha=" + fecha + ", tipo=" + tipo + ", hora=" + hora
                + ", motivo=" + (motivo == null ? null : "<redactado>")
                + ", rectificacionTardiaConfirmada=" + rectificacionTardiaConfirmada + "]";
    }
}
