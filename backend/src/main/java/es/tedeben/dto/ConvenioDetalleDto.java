package es.tedeben.dto;

import com.fasterxml.jackson.databind.JsonNode;
import es.tedeben.domain.convenio.Convenio;

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
