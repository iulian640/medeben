package es.medeben.dto;

import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Un apunte del diario tal y como lo ve el usuario (con su origen y su sello). */
public record ApunteResponse(
        LocalDate fecha,
        TipoApunte tipo,
        String hora,
        String motivo,
        OrigenApunte origen,
        OffsetDateTime registradoEn
) {

    public static ApunteResponse desde(Apunte a) {
        return new ApunteResponse(a.getFecha(), a.getTipo(), a.getHora(), a.getMotivo(),
                a.getOrigen(), a.getRegistradoEn());
    }

    /**
     * El toString autogenerado de un record volcaría el motivo (posible dato
     * de salud, art. 9 RGPD) en cualquier log accidental: aquí se redacta.
     */
    @Override
    public String toString() {
        return "ApunteResponse[fecha=" + fecha + ", tipo=" + tipo + ", hora=" + hora
                + ", motivo=" + (motivo == null ? null : "<redactado>")
                + ", origen=" + origen + ", registradoEn=" + registradoEn + "]";
    }
}
