package es.tedeben.dto;

import es.tedeben.domain.fichaje.Apunte;
import es.tedeben.domain.fichaje.OrigenApunte;
import es.tedeben.domain.fichaje.TipoApunte;

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
}
