package es.medeben.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.medeben.domain.convenio.Convenio;
import es.medeben.repository.ConvenioCatalog;
import es.medeben.repository.HechosCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #231: la restauración colectiva (comedores) nunca calculaba horas extra
 * porque su capa derivada no tenía jornada ni mensualidades. La jornada (1.800 h)
 * vive en el marco nacional del crudo y las pagas VARÍAN POR PROVINCIA (rareza
 * documentada del convenio), así que el motor las resuelve ahora como hechos
 * derivados con la dimensión provincia — las mismas dimensiones del perfil que
 * ya resuelven la tabla salarial.
 */
@DisplayName("Colectiva (#231) — horas extra por provincia desde la capa derivada")
class ColectivaHorasExtraTest {

    private static final String COLECTIVA = "estatal-restauracion-colectiva";

    /**
     * Cocinero/a de colectiva en Zaragoza (categoría del anexo provincial):
     * salario base 1.258,40 €/mes ya resolvía; ahora la cadena completa
     * tabla → motor debe valorar la hora: (1.258,40 × 14) / 1.800 = 9,7876.
     */
    private static final String CATEGORIA_COCINERO_ZARAGOZA =
            "Recepcionista / Administrativo/a / Técnico / Cocinero/a / Encargado de economato / "
                    + "Camarero/a / Supervisor/a de colectividades / Encargado/a de sección / Técnico de servicio";

    /** Anexos provinciales sin estructura de pagas publicada: su 422 es honesto (dato ausente > dato erróneo). */
    private static final Set<String> PENDIENTES_POR_PAGAS = Set.of(
            "Granada", "Santa Cruz de Tenerife", "Barcelona", "Girona", "Islas Baleares",
            "Alicante", "Valladolid", "Lleida", "Tarragona");

    private static ConvenioCatalog convenios;
    private static TablaSalarialService tablas;
    private static CalculoConvenioService motor;

    @BeforeAll
    static void arranque() {
        ObjectMapper mapper = new ObjectMapper();
        convenios = new ConvenioCatalog(mapper);
        HechosCatalog hechos = new HechosCatalog(mapper);
        tablas = new TablaSalarialService(hechos, convenios);
        motor = new CalculoConvenioService(hechos);
    }

    private static Convenio colectiva() {
        return convenios.porId(COLECTIVA).orElseThrow();
    }

    @Test
    @DisplayName("cadena completa: cocinero de colectiva en Zaragoza, 2026 → 10 h extra = 97,88 € con citas (no 422)")
    void cadenaCompletaZaragoza() {
        Map<String, String> dimensiones = Map.of(
                "provincia", "Zaragoza", "categoria", CATEGORIA_COCINERO_ZARAGOZA);

        // La tabla salarial ya funcionaba: el mínimo del convenio sale de la capa derivada...
        SalarioBaseResuelto salario = tablas
                .salarioBaseMinimo(COLECTIVA, dimensiones, LocalDate.of(2026, 7, 11))
                .orElseThrow();
        assertThat(salario.importe()).isEqualByComparingTo("1258.40");

        // ...y ahora el motor valora las horas extra con ese salario y esas MISMAS dimensiones.
        HorasExtraCalculadas resultado = motor.importeHorasExtra(
                        colectiva(), Year.of(2026), salario.importe(), BigDecimal.ZERO,
                        BigDecimal.TEN, dimensiones)
                .orElseThrow();

        // (1.258,40 × 14 pagas) / 1.800 h = 9,7876 €/h; 10 h → 97,88 €.
        assertThat(resultado.desglose().valorHora()).isEqualByComparingTo("9.7876");
        assertThat(resultado.desglose().mensualidades()).isEqualByComparingTo("14");
        assertThat(resultado.desglose().divisorHoras()).isEqualByComparingTo("1800");
        assertThat(resultado.desglose().esDivisorExplicito()).isFalse();
        assertThat(resultado.importe()).isEqualByComparingTo("97.88");

        // D34: citas de jornada (marco nacional) y pagas (anexo provincial), con enlace al BOE.
        assertThat(resultado.citas())
                .anySatisfy(c -> assertThat(c.texto()).contains("1800 h").contains("Marco nacional"))
                .anySatisfy(c -> assertThat(c.texto()).contains("14 mensualidades")
                        .contains("Anexo provincial Zaragoza"))
                .anySatisfy(c -> assertThat(c.texto()).contains("art. 35.1 ET"));
        assertThat(resultado.citas())
                .filteredOn(c -> c.texto().contains("Marco nacional"))
                .allSatisfy(c -> assertThat(c.url()).contains("boe.es"));
    }

    @Test
    @DisplayName("2026 cae fuera de la vigencia 2025 del dato: la cita avisa de la ultraactividad")
    void ultraactividadAvisadaEnCitas() {
        var resultado = motor.valorHoraOrdinaria(colectiva(), Year.of(2026),
                new BigDecimal("1200"), BigDecimal.ZERO, Map.of("provincia", "Zaragoza")).orElseThrow();

        assertThat(resultado.citas())
                .anySatisfy(c -> assertThat(c.texto()).contains("ultraactividad"));
    }

    @Test
    @DisplayName("en 2025 (vigencia publicada) no hay aviso de ultraactividad")
    void sinAvisoDeUltraactividadDentroDeVigencia() {
        var resultado = motor.valorHoraOrdinaria(colectiva(), Year.of(2025),
                new BigDecimal("1200"), BigDecimal.ZERO, Map.of("provincia", "Zaragoza")).orElseThrow();

        assertThat(resultado.citas())
                .noneSatisfy(c -> assertThat(c.texto()).contains("ultraactividad"));
    }

    @Test
    @DisplayName("las pagas varían por provincia: Sevilla tiene 15 mensualidades (no se generaliza)")
    void sevillaTieneQuincePagas() {
        var resultado = motor.valorHoraOrdinaria(colectiva(), Year.of(2026),
                new BigDecimal("1200"), BigDecimal.ZERO, Map.of("provincia", "Sevilla")).orElseThrow();

        assertThat(resultado.mensualidades()).isEqualByComparingTo("15");
        assertThat(resultado.valorHora()).isEqualByComparingTo("10.0000"); // 1.200×15/1.800
    }

    @Test
    @DisplayName("provincia sin pagas publicadas (Granada): no se calcula nada — su 422 es honesto")
    void granadaSiguePendiente() {
        assertThat(motor.valorHoraOrdinaria(colectiva(), Year.of(2026),
                new BigDecimal("1200"), BigDecimal.ZERO, Map.of("provincia", "Granada")))
                .isEmpty();
    }

    @Test
    @DisplayName("sin la dimensión provincia no se adivina ninguna: vacío (nunca se inventa)")
    void sinProvinciaNoSeInventa() {
        assertThat(motor.valorHoraOrdinaria(colectiva(), Year.of(2026),
                new BigDecimal("1200"), BigDecimal.ZERO, Map.of())).isEmpty();

        // La firma antigua (sin dimensiones) se comporta igual: vacío honesto.
        assertThat(motor.valorHoraOrdinaria(colectiva(), Year.of(2026),
                new BigDecimal("1200"), BigDecimal.ZERO)).isEmpty();
    }

    @Test
    @DisplayName("los demás convenios no cambian: Madrid sigue calculando por su nodo propio, con o sin dimensiones")
    void madridNoCambia() {
        Convenio madrid = convenios.porId("madrid-hosteleria").orElseThrow();
        var sinDimensiones = motor.valorHoraOrdinaria(madrid, Year.of(2026),
                new BigDecimal("1250.91"), new BigDecimal("2103.42")).orElseThrow();
        var conDimensiones = motor.valorHoraOrdinaria(madrid, Year.of(2026),
                new BigDecimal("1250.91"), new BigDecimal("2103.42"),
                Map.of("tabla", "general", "nivel", "III", "claseEmpresa", "B")).orElseThrow();

        assertThat(sinDimensiones.valorHora()).isEqualByComparingTo("10.8979");
        assertThat(conDimensiones.valorHora()).isEqualByComparingTo("10.8979");
    }

    /**
     * Barrido de cobertura del subsector: qué provincias del convenio resuelven
     * ya horas extra y cuáles quedan pendientes (y por qué). Si alguien deriva
     * pagas nuevas o el BOE publica las que faltan, este test obliga a
     * actualizar la foto — la cobertura no puede cambiar en silencio.
     */
    @Test
    @DisplayName("cobertura: 40 de 49 provincias resuelven horas extra; las 9 sin pagas publicadas quedan pendientes")
    void coberturaPorProvincia() {
        Set<String> resueltas = new TreeSet<>();
        Set<String> pendientes = new TreeSet<>();
        for (String provincia : provinciasDelCrudo()) {
            var valorHora = motor.valorHoraOrdinaria(colectiva(), Year.of(2026),
                    new BigDecimal("1200"), BigDecimal.ZERO, Map.of("provincia", provincia));
            if (valorHora.isPresent()) {
                resueltas.add(provincia);
                // Verificación provincia a provincia (regla crítica de la issue):
                // jornada del marco nacional y pagas del anexo, nunca generalizadas.
                assertThat(valorHora.orElseThrow().divisorHoras())
                        .as("jornada de %s", provincia).isEqualByComparingTo("1800");
                assertThat(valorHora.orElseThrow().mensualidades().intValueExact())
                        .as("pagas de %s", provincia).isIn(14, 15);
            } else {
                pendientes.add(provincia);
            }
        }

        assertThat(pendientes)
                .as("pendientes SOLO las provincias cuyo anexo no publica la estructura de pagas")
                .isEqualTo(new TreeSet<>(PENDIENTES_POR_PAGAS));
        assertThat(resueltas).hasSize(40);
    }

    /** Las 49 provincias con anexo en la transcripción (fuente de verdad del barrido). */
    private static Set<String> provinciasDelCrudo() {
        Set<String> provincias = new LinkedHashSet<>();
        JsonNode tablas = colectiva().raw().path("tablasPorProvincia");
        for (Iterator<String> it = tablas.fieldNames(); it.hasNext(); ) {
            String nombre = it.next();
            if (!"$comment".equals(nombre)) {
                provincias.add(nombre);
            }
        }
        assertThat(provincias).hasSize(49);
        return provincias;
    }
}
