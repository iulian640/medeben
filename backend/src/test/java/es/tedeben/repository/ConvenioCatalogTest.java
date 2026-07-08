package es.tedeben.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.tedeben.domain.convenio.Convenio;
import es.tedeben.domain.convenio.Subsector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("ConvenioCatalog — carga de los JSON de convenios del classpath")
class ConvenioCatalogTest {

    /** Ficheros en convenios/*.json; súbelo al añadir un convenio nuevo al corpus. */
    private static final int TAMANO_CORPUS = 55;

    private static ConvenioCatalog catalog;

    @BeforeAll
    static void cargaCatalogo() {
        catalog = new ConvenioCatalog(new ObjectMapper());
    }

    @Test
    @DisplayName("carga los 55 convenios del corpus")
    void cargaTodosLosConvenios() {
        assertThat(catalog.todos()).hasSize(TAMANO_CORPUS);
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

        assertThat(baleares.jornadaAnual()).contains(new BigDecimal("1776"));
    }

    @Test
    @DisplayName("el JSON crudo queda accesible para el motor de cálculo")
    void exponeJsonCrudo() {
        Convenio madrid = catalog.porId("madrid-hosteleria").orElseThrow();

        assertThat(madrid.raw().path("nocturnidad").path("articulo").asText())
                .isEqualTo("Art. 27");
    }

    @Nested
    @DisplayName("jornada anual por año (mapa de años, regla de última publicada)")
    class JornadaPorAnio {

        @Test
        @DisplayName("alava: mapa por año resuelve a la entrada del año pedido")
        void alavaResuelveAnioExacto() {
            Convenio alava = catalog.porId("alava-hosteleria").orElseThrow();

            assertThat(alava.jornadaAnual(Year.of(2025))).contains(new BigDecimal("1746"));
            assertThat(alava.jornadaAnual(Year.of(2026))).contains(new BigDecimal("1744"));
        }

        @Test
        @DisplayName("alava: un año posterior al último publicado usa la última entrada (ultraactividad)")
        void alavaAnioPosteriorUsaUltima() {
            Convenio alava = catalog.porId("alava-hosteleria").orElseThrow();

            assertThat(alava.jornadaAnual(Year.of(2035))).contains(new BigDecimal("1744"));
        }

        @Test
        @DisplayName("alava: un año anterior a toda entrada publicada devuelve vacío")
        void alavaAnioAnteriorVacio() {
            Convenio alava = catalog.porId("alava-hosteleria").orElseThrow();

            assertThat(alava.jornadaAnual(Year.of(2024))).isEmpty();
        }

        @Test
        @DisplayName("ceuta: jornada 'no_previsto' devuelve vacío en vez de inventar")
        void ceutaNoPrevisto() {
            Convenio ceuta = catalog.porId("ceuta-hosteleria").orElseThrow();

            assertThat(ceuta.jornadaAnual()).isEmpty();
        }
    }

    @Nested
    @DisplayName("validación fail-fast al parsear (un dato malo es peor que ninguno)")
    class ValidacionFailFast {

        private final ObjectMapper mapper = new ObjectMapper();

        private JsonNode json(String contenido) {
            try {
                return mapper.readTree(contenido);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }

        @Test
        @DisplayName("rechaza id que no coincide con el nombre del fichero")
        void rechazaIdDistintoDelFichero() {
            JsonNode raw = json("""
                    {"id":"otra-cosa","nombre":"X","ambitoFuncional":"hosteleria",
                     "ambitoTerritorial":{"tipo":"provincial"},"vigencia":{"desde":"2025-01-01"}}""");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Convenio.desdeJson("teruel-hosteleria.json", raw))
                    .withMessageContaining("no coincide");
        }

        @Test
        @DisplayName("rechaza vigencia.desde vacía")
        void rechazaVigenciaDesdeVacia() {
            JsonNode raw = json("""
                    {"id":"x-hosteleria","nombre":"X","ambitoFuncional":"hosteleria",
                     "ambitoTerritorial":{"tipo":"provincial"},"vigencia":{"desde":""}}""");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Convenio.desdeJson("x-hosteleria.json", raw))
                    .withMessageContaining("vigencia.desde");
        }

        @Test
        @DisplayName("rechaza subsector desconocido")
        void rechazaSubsectorDesconocido() {
            JsonNode raw = json("""
                    {"id":"x-hosteleria","nombre":"X","ambitoFuncional":"peluquería",
                     "ambitoTerritorial":{"tipo":"provincial"},"vigencia":{"desde":"2025-01-01"}}""");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Convenio.desdeJson("x-hosteleria.json", raw))
                    .withMessageContaining("Subsector desconocido");
        }

        @Test
        @DisplayName("rechaza provincias que no sea una lista")
        void rechazaProvinciasNoLista() {
            JsonNode raw = json("""
                    {"id":"x-hosteleria","nombre":"X","ambitoFuncional":"hosteleria",
                     "ambitoTerritorial":{"tipo":"provincial","provincias":"Madrid"},
                     "vigencia":{"desde":"2025-01-01"}}""");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Convenio.desdeJson("x-hosteleria.json", raw))
                    .withMessageContaining("provincias");
        }
    }
}
