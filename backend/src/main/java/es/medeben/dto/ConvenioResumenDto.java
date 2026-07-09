package es.medeben.dto;

import es.medeben.domain.convenio.Convenio;

import java.util.List;

/** Resumen de un convenio para listados y selección de perfil. */
public record ConvenioResumenDto(
        String id,
        String nombre,
        String subsector,
        String ambitoTipo,
        List<String> provincias,
        String vigenciaDesde,
        String vigenciaHasta,
        String estado,
        String fuenteUrl
) {

    public static ConvenioResumenDto desde(Convenio c) {
        return new ConvenioResumenDto(
                c.id(),
                c.nombre(),
                c.subsector().clave(),
                c.ambitoTerritorial().tipo(),
                c.ambitoTerritorial().provincias(),
                c.vigencia().desde(),
                c.vigencia().hasta(),
                c.raw().path("estado").asText(null),
                c.fuenteUrl());
    }
}
