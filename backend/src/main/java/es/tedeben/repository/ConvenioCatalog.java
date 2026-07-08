package es.tedeben.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.tedeben.domain.convenio.Convenio;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fuente de lectura de los convenios: los JSON de `convenios/` (raíz del repo)
 * empaquetados en el classpath. Carga y valida todo el corpus en el arranque —
 * un convenio malformado tumba la app a propósito (D24: el seed valida).
 */
@Component
public class ConvenioCatalog {

    private static final String PATRON_CONVENIOS = "classpath*:convenios/*.json";

    private final Map<String, Convenio> conveniosPorId;

    public ConvenioCatalog(ObjectMapper objectMapper) {
        this.conveniosPorId = carga(objectMapper);
    }

    public List<Convenio> todos() {
        return List.copyOf(conveniosPorId.values());
    }

    public Optional<Convenio> porId(String id) {
        return Optional.ofNullable(conveniosPorId.get(id));
    }

    private static Map<String, Convenio> carga(ObjectMapper objectMapper) {
        Resource[] recursos;
        try {
            recursos = new PathMatchingResourcePatternResolver().getResources(PATRON_CONVENIOS);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudieron listar los convenios del classpath", e);
        }
        if (recursos.length == 0) {
            throw new IllegalStateException(
                    "No se encontró ningún convenio en el classpath (¿falta el mapeo de recursos de ../convenios en el pom?)");
        }

        Map<String, Convenio> porId = new LinkedHashMap<>();
        for (Resource recurso : recursos) {
            String fichero = recurso.getFilename() == null ? recurso.getDescription() : recurso.getFilename();
            Convenio convenio = parsea(objectMapper, recurso, fichero);
            Convenio anterior = porId.put(convenio.id(), convenio);
            if (anterior != null) {
                throw new IllegalStateException("Convenio con id duplicado: " + convenio.id());
            }
        }
        return porId;
    }

    private static Convenio parsea(ObjectMapper objectMapper, Resource recurso, String fichero) {
        try (InputStream in = recurso.getInputStream()) {
            JsonNode raw = objectMapper.readTree(in);
            return Convenio.desdeJson(fichero, raw);
        } catch (IOException e) {
            throw new UncheckedIOException("Convenio ilegible: " + fichero, e);
        }
    }
}
