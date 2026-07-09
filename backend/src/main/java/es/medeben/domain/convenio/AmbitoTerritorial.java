package es.medeben.domain.convenio;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Ámbito territorial de un convenio. Los estatales usan {tipo, cobertura} en vez
 * de {tipo, comunidad, provincias}; ambos campos opcionales conviven aquí.
 */
public record AmbitoTerritorial(
        String tipo,
        String comunidad,
        List<String> provincias,
        String cobertura
) {

    public AmbitoTerritorial {
        provincias = List.copyOf(provincias);
    }

    static AmbitoTerritorial desdeJson(JsonNode nodo) {
        List<String> provincias = new ArrayList<>();
        for (JsonNode p : nodo.path("provincias")) {
            provincias.add(p.asText());
        }
        return new AmbitoTerritorial(
                nodo.path("tipo").asText(null),
                nodo.path("comunidad").asText(null),
                provincias,
                nodo.path("cobertura").asText(null));
    }
}
