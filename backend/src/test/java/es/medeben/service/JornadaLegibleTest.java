package es.medeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.medeben.domain.convenio.Convenio;
import es.medeben.repository.ConvenioCatalog;
import es.medeben.repository.HechosCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fija que los convenios re-estructurados en la ronda de legibilidad quedan
 * LEGIBLES para el motor: jornada anual bajo la clave canónica, mensualidades
 * resolubles y divisor de valor hora explícito. Ningún valor cambió — solo las
 * claves (regla sagrada del corpus: nunca inventar).
 */
@DisplayName("Legibilidad del corpus — jornada, pagas y divisor bajo claves canónicas")
class JornadaLegibleTest {

    private static final BigDecimal SALARIO_BASE = new BigDecimal("1200");

    private static ConvenioCatalog catalog;
    private static CalculoConvenioService servicio;

    @BeforeAll
    static void arranque() {
        ObjectMapper mapper = new ObjectMapper();
        catalog = new ConvenioCatalog(mapper);
        servicio = new CalculoConvenioService(new HechosCatalog(mapper));
    }

    private Convenio convenio(String id) {
        return catalog.porId(id).orElseThrow();
    }

    private ValorHoraCalculado valorHora(String id, int anio) {
        return servicio.valorHoraOrdinaria(convenio(id), Year.of(anio), SALARIO_BASE, BigDecimal.ZERO)
                .orElseThrow();
    }

    @Nested
    @DisplayName("cataluña: jornada por año bajo jornadaAnual.horas (transición 1791→1783)")
    class Cataluna {

        @Test
        @DisplayName("la jornada anual resuelve por año publicado")
        void jornadaResuelve() {
            Convenio cataluna = convenio("cataluna-hosteleria");

            assertThat(cataluna.jornadaAnual(Year.of(2025))).contains(new BigDecimal("1791"));
            assertThat(cataluna.jornadaAnual(Year.of(2026))).contains(new BigDecimal("1783"));
        }

        @Test
        @DisplayName("el valor hora calcula con la jornada del año: (1200 × 14) / 1783")
        void valorHoraCalcula() {
            var resultado = valorHora("cataluna-hosteleria", 2026);

            assertThat(resultado.valorHora()).isEqualByComparingTo(new BigDecimal("9.4223"));
            assertThat(resultado.citas())
                    .anySatisfy(cita -> assertThat(cita.texto()).contains("Art. 28"));
        }
    }

    @Nested
    @DisplayName("gipuzkoa hospedaje: la clave canónica lleva la jornada GENERAL")
    class GipuzkoaHospedaje {

        @Test
        @DisplayName("la jornada anual (colectivo general) resuelve por año")
        void jornadaGeneralResuelve() {
            Convenio gipuzkoa = convenio("gipuzkoa-hospedaje");

            assertThat(gipuzkoa.jornadaAnual(Year.of(2025))).contains(new BigDecimal("1723"));
            assertThat(gipuzkoa.jornadaAnual(Year.of(2026))).contains(new BigDecimal("1719"));
        }

        @Test
        @DisplayName("la jornada reducida de camareras de piso se conserva como sub-detalle")
        void camarerasDePisoConservada() {
            Convenio gipuzkoa = convenio("gipuzkoa-hospedaje");

            assertThat(gipuzkoa.raw().path("jornadaAnual").path("camarerasDePiso").path("2025").asInt())
                    .isEqualTo(1709);
        }

        @Test
        @DisplayName("el valor hora calcula: (1200 × 14) / 1719")
        void valorHoraCalcula() {
            assertThat(valorHora("gipuzkoa-hospedaje", 2026).valorHora())
                    .isEqualByComparingTo(new BigDecimal("9.7731"));
        }
    }

    @Nested
    @DisplayName("soria: mapa por año canónico en vez de claves ad-hoc horas2022a2024/horas2025")
    class Soria {

        @Test
        @DisplayName("la jornada anual resuelve para cada año publicado")
        void jornadaResuelve() {
            Convenio soria = convenio("soria-hosteleria");

            assertThat(soria.jornadaAnual(Year.of(2022))).contains(new BigDecimal("1785"));
            assertThat(soria.jornadaAnual(Year.of(2024))).contains(new BigDecimal("1785"));
            assertThat(soria.jornadaAnual(Year.of(2025))).contains(new BigDecimal("1780"));
        }

        @Test
        @DisplayName("2026 usa la última publicada (ultraactividad): 1780")
        void ultraactividad() {
            assertThat(convenio("soria-hosteleria").jornadaAnual(Year.of(2026)))
                    .contains(new BigDecimal("1780"));
        }

        @Test
        @DisplayName("el valor hora calcula: (1200 × 14) / 1780")
        void valorHoraCalcula() {
            assertThat(valorHora("soria-hosteleria", 2026).valorHora())
                    .isEqualByComparingTo(new BigDecimal("9.4382"));
        }
    }

    @Nested
    @DisplayName("tenerife: divisor de valor hora explícito del convenio (1.829 h, Arts. 23 y 24)")
    class Tenerife {

        @Test
        @DisplayName("sin jornada anual fijada, el divisor explícito permite calcular: (1200 × 14) / 1829")
        void divisorExplicitoCalcula() {
            Convenio tenerife = convenio("tenerife-hosteleria");
            assertThat(tenerife.jornadaAnual(Year.of(2026))).isEmpty();

            var resultado = valorHora("tenerife-hosteleria", 2026);

            assertThat(resultado.valorHora()).isEqualByComparingTo(new BigDecimal("9.1853"));
            assertThat(resultado.divisorHoras()).isEqualByComparingTo(new BigDecimal("1829"));
            // Clave para el frontend: NO es jornada anual (Tenerife no la fija) y
            // el desglose no debe etiquetarlo como tal.
            assertThat(resultado.esDivisorExplicito()).isTrue();
        }

        @Test
        @DisplayName("el resultado cita el divisor con su artículo (D34)")
        void citaElDivisor() {
            assertThat(valorHora("tenerife-hosteleria", 2026).citas())
                    .anySatisfy(cita -> assertThat(cita.texto())
                            .contains("1829")
                            .contains("Arts. 23 y 24"));
        }

        @Test
        @DisplayName("el divisor explícito tiene prioridad sobre la jornada anual si coexisten")
        void divisorGanaAJornada() throws Exception {
            Convenio sintetico = Convenio.desdeJson("x-hosteleria.json", new ObjectMapper().readTree("""
                    {"id":"x-hosteleria","nombre":"Sintético","ambitoFuncional":"hosteleria",
                     "ambitoTerritorial":{"tipo":"provincial"},"vigencia":{"desde":"2025-01-01"},
                     "jornadaAnual":{"horas":1800,"articulo":"Art. 10"},
                     "divisorValorHora":{"horas":2000,"articulo":"Art. 99"},
                     "pagasExtraordinarias":{"cantidad":2}}"""));

            var resultado = servicio.valorHoraOrdinaria(
                    sintetico, Year.of(2026), SALARIO_BASE, BigDecimal.ZERO).orElseThrow();

            assertThat(resultado.valorHora()).isEqualByComparingTo(new BigDecimal("8.4000"));
            assertThat(resultado.esDivisorExplicito()).isTrue();
            assertThat(resultado.citas())
                    .anySatisfy(cita -> assertThat(cita.texto()).contains("Art. 99"))
                    .noneSatisfy(cita -> assertThat(cita.texto()).contains("Art. 10"));
        }
    }

    @Nested
    @DisplayName("lugo: mensualidadesEquivalentes canónico en vez de pagas en texto libre")
    class Lugo {

        @Test
        @DisplayName("el valor hora calcula con 14 mensualidades: (1200 × 14) / 1800")
        void valorHoraCalcula() {
            var resultado = valorHora("lugo-hosteleria", 2026);

            assertThat(resultado.valorHora()).isEqualByComparingTo(new BigDecimal("9.3333"));
            assertThat(resultado.citas())
                    .anySatisfy(cita -> assertThat(cita.texto()).contains("14 mensualidades"));
        }
    }
}
