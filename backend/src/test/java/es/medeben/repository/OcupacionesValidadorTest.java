package es.medeben.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.medeben.domain.convenio.Hecho;
import es.medeben.service.Puesto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validador cruzado de los mapeos puesto→dimensiones: cada mapeo debe casar
 * con hechos reales de la capa normalizada y usar solo puestos de la lista
 * curada. Vigila tanto los mapeos hechos a mano como los generados por agentes.
 */
@DisplayName("Mapeos de ocupaciones — validación cruzada contra la capa normalizada")
class OcupacionesValidadorTest {

    private static OcupacionesCatalog ocupaciones;
    private static HechosCatalog hechos;
    private static ConvenioCatalog convenios;

    @BeforeAll
    static void carga() {
        ObjectMapper mapper = new ObjectMapper();
        ocupaciones = new OcupacionesCatalog(mapper);
        hechos = new HechosCatalog(mapper);
        convenios = new ConvenioCatalog(mapper);
    }

    @Test
    @DisplayName("hay mapeos cargados (al menos el piloto de Madrid)")
    void hayMapeos() {
        assertThat(ocupaciones.mapeo("madrid-hosteleria")).isPresent();
    }

    @Test
    @DisplayName("todo convenio mapeado existe en el catálogo de transcripciones")
    void convenioExiste() {
        for (String id : idsMapeados()) {
            assertThat(convenios.porId(id)).as("transcripción de %s", id).isPresent();
        }
    }

    @Test
    @DisplayName("los puestos de cada mapeo pertenecen a la lista curada")
    void puestosDeLaListaCurada() {
        Set<String> curados = ocupaciones.puestos().stream().map(Puesto::id).collect(Collectors.toSet());
        for (String id : idsMapeados()) {
            var mapeo = ocupaciones.mapeo(id).orElseThrow();
            for (String puesto : mapeo.dimensionesPorPuesto().keySet()) {
                assertThat(curados).as("%s: puesto '%s' fuera de la lista curada", id, puesto).contains(puesto);
            }
            // Los condicionales también: un id con typo aquí sería datos muertos
            // (nunca se resolvería) y no lo cazaría ningún otro test.
            for (String puesto : mapeo.condicionalPorPuesto().keySet()) {
                assertThat(curados).as("%s: puesto condicional '%s' fuera de la lista curada", id, puesto).contains(puesto);
            }
        }
    }

    @Test
    @DisplayName("PROCEDENCIA: cada puesto mapeado casa con al menos un hecho de salarioBase")
    void mapeosCasanConHechos() {
        for (String id : idsMapeados()) {
            var mapeo = ocupaciones.mapeo(id).orElseThrow();
            List<Hecho> hechosConvenio = hechos.deConvenio(id).stream()
                    .filter(h -> "salarioBase".equals(h.concepto()))
                    .toList();
            mapeo.dimensionesPorPuesto().forEach((puesto, dims) -> {
                boolean casa = hechosConvenio.stream().anyMatch(h -> contiene(h.dimensiones(), dims));
                assertThat(casa)
                        .as("%s: el puesto '%s' con dimensiones %s no casa con ningún hecho", id, puesto, dims)
                        .isTrue();
            });
        }
    }

    private static Set<String> idsMapeados() {
        return convenios.todos().stream()
                .map(c -> c.id())
                .filter(id -> ocupaciones.mapeo(id).isPresent())
                .collect(Collectors.toSet());
    }

    private static boolean contiene(Map<String, String> dimensiones, Map<String, String> fijas) {
        return fijas.entrySet().stream()
                .allMatch(e -> e.getValue().equals(dimensiones.get(e.getKey())));
    }
}
