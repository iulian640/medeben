package es.tedeben.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.tedeben.domain.convenio.Hecho;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carga la capa derivada normalizada (convenios/normalizado/*.json). A diferencia
 * de las transcripciones, aquí NO es obligatorio que estén los 55: un convenio sin
 * derivar simplemente no ofrece lookup de tablas (la app cae al modo manual).
 * La coherencia con las transcripciones la vigila CapaNormalizadaValidadorTest.
 */
@Component
public class HechosCatalog {

    private static final String PATRON_HECHOS = "classpath*:convenios/normalizado/*.json";

    private final Map<String, List<Hecho>> hechosPorConvenio;

    public HechosCatalog(ObjectMapper objectMapper) {
        this.hechosPorConvenio = carga(objectMapper);
    }

    public Set<String> conveniosDerivados() {
        return hechosPorConvenio.keySet();
    }

    /** Hechos del convenio; lista vacía si aún no está derivado. */
    public List<Hecho> deConvenio(String convenioId) {
        return hechosPorConvenio.getOrDefault(convenioId, List.of());
    }

    private static Map<String, List<Hecho>> carga(ObjectMapper objectMapper) {
        Resource[] recursos;
        try {
            recursos = new PathMatchingResourcePatternResolver().getResources(PATRON_HECHOS);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo listar convenios/normalizado del classpath", e);
        }

        Map<String, List<Hecho>> porConvenio = new LinkedHashMap<>();
        for (Resource recurso : recursos) {
            String fichero = recurso.getFilename() == null ? recurso.getDescription() : recurso.getFilename();
            try (InputStream in = recurso.getInputStream()) {
                JsonNode raiz = objectMapper.readTree(in);
                String id = raiz.path("id").asText(null);
                if (id == null || id.isBlank()) {
                    throw new IllegalArgumentException(fichero + ": falta el campo 'id'");
                }
                List<Hecho> hechos = new ArrayList<>();
                for (JsonNode nodo : raiz.path("hechos")) {
                    hechos.add(Hecho.desdeJson(fichero, nodo));
                }
                if (hechos.isEmpty()) {
                    throw new IllegalArgumentException(fichero + ": capa derivada sin hechos");
                }
                if (porConvenio.put(id, List.copyOf(hechos)) != null) {
                    throw new IllegalStateException("Capa derivada duplicada para: " + id);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Capa derivada ilegible: " + fichero, e);
            }
        }
        return porConvenio;
    }
}
