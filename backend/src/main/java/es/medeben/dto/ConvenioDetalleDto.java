package es.medeben.dto;

import com.fasterxml.jackson.databind.JsonNode;
import es.medeben.domain.convenio.Convenio;

/**
 * Detalle de un convenio: resumen + la transcripción completa (`convenio`),
 * que alimenta el visor del convenio en la app (D4).
 */
public record ConvenioDetalleDto(
        String id,
        String nombre,
        String subsector,
        JsonNode convenio
) {

    public static ConvenioDetalleDto desde(Convenio c) {
        return new ConvenioDetalleDto(c.id(), c.nombre(), c.subsector().clave(), c.raw());
    }
}
