package es.tedeben.repository;

import es.tedeben.domain.convenio.Convenio;
import es.tedeben.domain.convenio.Subsector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConvenioCatalog — carga de los JSON de convenios del classpath")
class ConvenioCatalogTest {

    private static ConvenioCatalog catalog;

    @BeforeAll
    static void cargaCatalogo() {
        catalog = new ConvenioCatalog(new ObjectMapper());
    }

    @Test
    @DisplayName("carga los 55 convenios del corpus")
    void cargaTodosLosConvenios() {
        assertThat(catalog.todos()).hasSize(55);
    }

    @Test
    @DisplayName("los ids son únicos")
    void idsUnicos() {
        Set<String> ids = new HashSet<>();
        for (Convenio c : catalog.todos()) {
            assertThat(ids.add(c.id()))
                    .as("id duplicado: %s", c.id())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("todo convenio tiene id, nombre, subsector y vigencia desde")
    void camposObligatoriosPresentes() {
        for (Convenio c : catalog.todos()) {
            assertThat(c.id()).as("id").isNotBlank();
            assertThat(c.nombre()).as("nombre de %s", c.id()).isNotBlank();
            assertThat(c.subsector()).as("subsector de %s", c.id()).isNotNull();
            assertThat(c.vigencia().desde()).as("vigencia.desde de %s", c.id()).isNotBlank();
        }
    }

    @Test
    @DisplayName("porId encuentra un convenio existente y devuelve empty si no existe")
    void porId() {
        assertThat(catalog.porId("madrid-hosteleria")).isPresent();
        assertThat(catalog.porId("narnia-hosteleria")).isEmpty();
    }

    @Test
    @DisplayName("madrid-hosteleria: autonómico, subsector hostelería, jornada 1800 h")
    void madridHosteleriaParseado() {
        Convenio madrid = catalog.porId("madrid-hosteleria").orElseThrow();

        assertThat(madrid.subsector()).isEqualTo(Subsector.HOSTELERIA);
        assertThat(madrid.ambitoTerritorial().tipo()).isEqualTo("autonomico");
        assertThat(madrid.ambitoTerritorial().provincias()).containsExactly("Madrid");
        assertThat(madrid.jornadaAnual()).contains(new BigDecimal("1800"));
        assertThat(madrid.vigencia().hastaFecha()).contains(LocalDate.of(2025, 12, 31));
    }

    @Test
    @DisplayName("aleh-estatal: tolera vigencia 'desde 2023 / hasta pendiente' y ausencia de jornada")
    void alehEstatalTolerado() {
        Convenio aleh = catalog.porId("aleh-estatal").orElseThrow();

        assertThat(aleh.vigencia().desde()).isEqualTo("2023");
        assertThat(aleh.vigencia().desdeFecha()).isEmpty();
        assertThat(aleh.vigencia().hastaFecha()).isEmpty();
        assertThat(aleh.jornadaAnual()).isEmpty();
    }

    @Test
    @DisplayName("baleares-hosteleria: lee la jornada anual del bloque alternativo 'jornada'")
    void balearesJornadaAlternativa() {
        Convenio baleares = catalog.porId("baleares-hosteleria").orElseThrow();

        assertThat(baleares.jornadaAnual()).isPresent();
    }

    @Test
    @DisplayName("el JSON crudo queda accesible para el motor de cálculo")
    void exponeJsonCrudo() {
        Convenio madrid = catalog.porId("madrid-hosteleria").orElseThrow();

        assertThat(madrid.raw().path("nocturnidad").path("articulo").asText())
                .isEqualTo("Art. 27");
    }
}
