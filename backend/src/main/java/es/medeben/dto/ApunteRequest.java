package es.medeben.dto;

import es.medeben.domain.fichaje.TipoApunte;
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
        @NotNull(message = "no puede faltar") LocalDate fecha,
        @NotNull(message = "no puede faltar") TipoApunte tipo,
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "hora en formato HH:mm") String hora,
        @Size(max = 200, message = "no puede pasar de 200 caracteres") String motivo,
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
