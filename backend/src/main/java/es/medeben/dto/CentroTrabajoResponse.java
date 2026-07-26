package es.medeben.dto;

import es.medeben.domain.fichaje.CentroTrabajo;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * El centro de trabajo vigente, SIN coordenadas (corrección ALTO del
 * verificador rgpd-play: "ninguna coordenada sale en respuestas de API de uso
 * ordinario"). Solo lo que la UI necesita para mostrar "declarado el X".
 *
 * <p>{@code id} SÍ va (no es coordenada, no viola la minimización): sin él,
 * {@code DELETE /api/v1/centro-trabajo/{id}?purgar=true} era inalcanzable
 * desde el cliente — ningún endpoint devolvía el UUID que ese path necesita.</p>
 */
public record CentroTrabajoResponse(UUID id, String alias, int radioMetros, OffsetDateTime declaradoEn) {

    public static CentroTrabajoResponse desde(CentroTrabajo c) {
        return new CentroTrabajoResponse(c.getId(), c.getAlias(), c.getRadioMetros(), c.getDeclaradoEn());
    }
}
