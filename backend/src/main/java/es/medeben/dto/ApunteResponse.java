package es.medeben.dto;

import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un apunte del diario tal y como lo ve el usuario (con su origen y su sello).
 *
 * <p>{@code id} (contrato de "Anotar dónde fichas", cambio aditivo D9/§4):
 * sin él el cliente no conoce el id del apunte recién creado y no puede
 * adjuntarle una ubicación con {@code POST /fichajes/{id}/ubicacion}.
 * Excepción de regresión admitida: cualquier assert que fijara los campos
 * exactos de este record.</p>
 */
public record ApunteResponse(
        UUID id,
        LocalDate fecha,
        TipoApunte tipo,
        String hora,
        String motivo,
        OrigenApunte origen,
        OffsetDateTime registradoEn
) {

    public static ApunteResponse desde(Apunte a) {
        return new ApunteResponse(a.getId(), a.getFecha(), a.getTipo(), a.getHora(), a.getMotivo(),
                a.getOrigen(), a.getRegistradoEn());
    }

    /**
     * El toString autogenerado de un record volcaría el motivo (posible dato
     * de salud, art. 9 RGPD) en cualquier log accidental: aquí se redacta.
     */
    @Override
    public String toString() {
        return "ApunteResponse[id=" + id + ", fecha=" + fecha + ", tipo=" + tipo + ", hora=" + hora
                + ", motivo=" + (motivo == null ? null : "<redactado>")
                + ", origen=" + origen + ", registradoEn=" + registradoEn + "]";
    }
}
