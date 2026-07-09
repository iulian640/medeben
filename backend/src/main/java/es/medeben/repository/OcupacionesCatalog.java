package es.medeben.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.medeben.service.Puesto;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Carga la lista curada de puestos (_puestos.json) y los mapeos
 * puesto→dimensiones por convenio (convenios/ocupaciones/*.json). Un convenio
 * sin mapeo simplemente no ofrece la selección por puesto (modo manual);
 * la coherencia con la capa normalizada la vigila el validador del build.
 */
@Component
public class OcupacionesCatalog {

    private static final String FICHERO_PUESTOS = "classpath:convenios/ocupaciones/_puestos.json";
    private static final String PATRON_MAPEOS = "classpath*:convenios/ocupaciones/*.json";

    private final List<Puesto> puestos;
    private final Map<String, MapeoConvenio> mapeosPorConvenio;

    public OcupacionesCatalog(ObjectMapper objectMapper) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        this.puestos = cargaPuestos(objectMapper, resolver);
        this.mapeosPorConvenio = cargaMapeos(objectMapper, resolver);
    }

    public List<Puesto> puestos() {
        return puestos;
    }

    /** Mapeo puestoId→dimensiones del convenio; entrada ausente = puesto no contemplado. */
    public Optional<MapeoConvenio> mapeo(String convenioId) {
        return Optional.ofNullable(mapeosPorConvenio.get(convenioId));
    }

    public record MapeoConvenio(String articulo, Map<String, Map<String, String>> dimensionesPorPuesto) {

        public MapeoConvenio {
            dimensionesPorPuesto = Map.copyOf(dimensionesPorPuesto);
        }
    }

    private static List<Puesto> cargaPuestos(ObjectMapper objectMapper,
                                             PathMatchingResourcePatternResolver resolver) {
        Resource recurso = resolver.getResource(FICHERO_PUESTOS);
        try (InputStream in = recurso.getInputStream()) {
            JsonNode raiz = objectMapper.readTree(in);
            List<Puesto> lista = new ArrayList<>();
            for (JsonNode nodo : raiz.path("puestos")) {
                String id = nodo.path("id").asText(null);
                String etiqueta = nodo.path("etiqueta").asText(null);
                if (id == null || etiqueta == null) {
                    throw new IllegalArgumentException("_puestos.json: puesto sin id o etiqueta");
                }
                lista.add(new Puesto(id, etiqueta));
            }
            if (lista.isEmpty()) {
                throw new IllegalStateException("_puestos.json sin puestos");
            }
            return List.copyOf(lista);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + FICHERO_PUESTOS, e);
        }
    }

    private static Map<String, MapeoConvenio> cargaMapeos(ObjectMapper objectMapper,
                                                          PathMatchingResourcePatternResolver resolver) {
        Resource[] recursos;
        try {
            recursos = resolver.getResources(PATRON_MAPEOS);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo listar convenios/ocupaciones", e);
        }

        Map<String, MapeoConvenio> mapeos = new LinkedHashMap<>();
        for (Resource recurso : recursos) {
            String fichero = recurso.getFilename() == null ? recurso.getDescription() : recurso.getFilename();
            if ("_puestos.json".equals(fichero)) {
                continue;
            }
            try (InputStream in = recurso.getInputStream()) {
                JsonNode raiz = objectMapper.readTree(in);
                String id = raiz.path("id").asText(null);
                if (id == null || id.isBlank()) {
                    throw new IllegalArgumentException(fichero + ": falta el campo 'id'");
                }
                Map<String, Map<String, String>> porPuesto = new LinkedHashMap<>();
                JsonNode ocupaciones = raiz.path("ocupaciones");
                for (Iterator<Map.Entry<String, JsonNode>> it = ocupaciones.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> entrada = it.next();
                    JsonNode dimensiones = entrada.getValue().path("dimensiones");
                    if (!dimensiones.isObject() || dimensiones.isEmpty()) {
                        continue; // null o sin dimensiones = puesto no contemplado
                    }
                    Map<String, String> dims = new LinkedHashMap<>();
                    for (Iterator<Map.Entry<String, JsonNode>> dit = dimensiones.fields(); dit.hasNext(); ) {
                        Map.Entry<String, JsonNode> d = dit.next();
                        dims.put(d.getKey(), d.getValue().asText());
                    }
                    porPuesto.put(entrada.getKey(), Map.copyOf(dims));
                }
                if (mapeos.put(id, new MapeoConvenio(raiz.path("articulo").asText(null), porPuesto)) != null) {
                    throw new IllegalStateException("Mapeo de ocupaciones duplicado: " + id);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Mapeo de ocupaciones ilegible: " + fichero, e);
            }
        }
        return mapeos;
    }
}
