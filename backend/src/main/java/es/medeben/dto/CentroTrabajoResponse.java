package es.medeben.dto;

import es.medeben.domain.fichaje.CentroTrabajo;

import java.time.OffsetDateTime;

/**
 * El centro de trabajo vigente, SIN coordenadas (corrección ALTO del
 * verificador rgpd-play: "ninguna coordenada sale en respuestas de API de uso
 * ordinario"). Solo lo que la UI necesita para mostrar "declarado el X".
 */
public record CentroTrabajoResponse(String alias, int radioMetros, OffsetDateTime declaradoEn) {

    public static CentroTrabajoResponse desde(CentroTrabajo c) {
        return new CentroTrabajoResponse(c.getAlias(), c.getRadioMetros(), c.getDeclaradoEn());
    }
}
