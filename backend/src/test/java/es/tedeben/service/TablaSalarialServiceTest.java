package es.tedeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.tedeben.repository.ConvenioCatalog;
import es.tedeben.repository.HechosCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TablaSalarialService — salario base mínimo por dimensiones y fecha (capa normalizada)")
class TablaSalarialServiceTest {

    private static final Map<String, String> COCINERO_MADRID_B =
            Map.of("tabla", "general", "nivel", "III", "claseEmpresa", "B");

    private static TablaSalarialService servicio;

    @BeforeAll
    static void arranque() {
        ObjectMapper mapper = new ObjectMapper();
        servicio = new TablaSalarialService(new HechosCatalog(mapper), new ConvenioCatalog(mapper));
    }

    @Test
    @DisplayName("madrid: cocinero (nivel III) clase B en 2025 → 1.250,91 € con su artículo")
    void madridDentroDeVigencia() {
        var resultado = servicio.salarioBaseMinimo(
                "madrid-hosteleria", COCINERO_MADRID_B, LocalDate.of(2025, 6, 1)).orElseThrow();

        assertThat(resultado.importe()).isEqualByComparingTo(new BigDecimal("1250.91"));
        assertThat(resultado.citas()).anySatisfy(c -> assertThat(c.texto()).contains("Anexo I C) c)"));
    }

    @Test
    @DisplayName("madrid en 2026: convenio vencido → aplica la última tabla publicada (ultraactividad) y lo avisa")
    void madridUltraactividad() {
        var resultado = servicio.salarioBaseMinimo(
                "madrid-hosteleria", COCINERO_MADRID_B, LocalDate.of(2026, 7, 8)).orElseThrow();

        assertThat(resultado.importe()).isEqualByComparingTo(new BigDecimal("1250.91"));
        assertThat(resultado.citas()).anySatisfy(c -> assertThat(c.texto()).contains("ultraactividad"));
    }

    @Test
    @DisplayName("madrid catering: dimensiones propias del catering, sin clase de empresa")
    void madridCatering() {
        var resultado = servicio.salarioBaseMinimo(
                "madrid-hosteleria", Map.of("tabla", "catering", "nivel", "II-C"),
                LocalDate.of(2025, 6, 1)).orElseThrow();

        assertThat(resultado.importe()).isEqualByComparingTo(new BigDecimal("1307.48"));
    }

    @Test
    @DisplayName("baleares: los periodos abril→marzo se respetan como rangos de fecha normales")
    void balearesPeriodosAbrilMarzo() {
        Map<String, String> camarero = Map.of("nivel", "IV", "categoriaEstablecimiento", "A");

        // Febrero de 2026 cae AÚN en el periodo 2025-26 (abr 2025 → mar 2026)
        assertThat(servicio.salarioBaseMinimo("baleares-hosteleria", camarero,
                LocalDate.of(2026, 2, 1)).orElseThrow().importe())
                .isEqualByComparingTo(new BigDecimal("1781.32"));

        // Junio de 2026 ya es el periodo 2026-27
        assertThat(servicio.salarioBaseMinimo("baleares-hosteleria", camarero,
                LocalDate.of(2026, 6, 1)).orElseThrow().importe())
                .isEqualByComparingTo(new BigDecimal("1852.57"));
    }

    @Test
    @DisplayName("la unidad del hecho se expone: Cuenca publica salarios en EUR/año, Madrid en EUR/mes")
    void unidadExpuesta() {
        var madrid = servicio.salarioBaseMinimo(
                "madrid-hosteleria", COCINERO_MADRID_B, LocalDate.of(2025, 6, 1)).orElseThrow();
        assertThat(madrid.unidad()).isEqualTo("EUR/mes");

        var cuenca = servicio.salarioBaseMinimo(
                "cuenca-hosteleria", Map.of("nivel", "I", "grupoEstablecimiento", "A"),
                LocalDate.of(2025, 6, 1)).orElseThrow();
        assertThat(cuenca.unidad()).isEqualTo("EUR/año");
        assertThat(cuenca.importe()).isEqualByComparingTo(new BigDecimal("16175.05"));
    }

    @Test
    @DisplayName("dimensiones que no existen en el convenio → vacío, no se inventa")
    void dimensionesInexistentes() {
        assertThat(servicio.salarioBaseMinimo("madrid-hosteleria",
                Map.of("tabla", "general", "nivel", "XX", "claseEmpresa", "B"),
                LocalDate.of(2025, 6, 1))).isEmpty();
    }

    @Test
    @DisplayName("fecha anterior a toda tabla publicada → vacío")
    void fechaAnteriorATodo() {
        assertThat(servicio.salarioBaseMinimo("madrid-hosteleria", COCINERO_MADRID_B,
                LocalDate.of(2010, 1, 1))).isEmpty();
    }

    @Test
    @DisplayName("convenio sin capa derivada todavía → vacío (no bloquea, cae al modo manual)")
    void convenioSinDerivar() {
        assertThat(servicio.salarioBaseMinimo("teruel-hosteleria", Map.of("nivel", "1,50"),
                LocalDate.of(2025, 6, 1))).isEmpty();
    }

    @Test
    @DisplayName("cadena completa: salario de tabla + motor = valor hora del cocinero de Madrid")
    void cadenaCompletaConMotor() {
        var catalog = new es.tedeben.repository.ConvenioCatalog(new ObjectMapper());
        var motor = new CalculoConvenioService();

        var salario = servicio.salarioBaseMinimo(
                "madrid-hosteleria", COCINERO_MADRID_B, LocalDate.of(2026, 7, 8)).orElseThrow();
        var valorHora = motor.valorHoraOrdinaria(
                catalog.porId("madrid-hosteleria").orElseThrow(),
                java.time.Year.of(2026), salario.importe(), new BigDecimal("2103.42")).orElseThrow();

        assertThat(valorHora.valorHora()).isEqualByComparingTo(new BigDecimal("10.8979"));
    }
}
