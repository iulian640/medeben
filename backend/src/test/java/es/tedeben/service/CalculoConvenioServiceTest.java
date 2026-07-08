package es.tedeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.tedeben.domain.convenio.Convenio;
import es.tedeben.repository.ConvenioCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CalculoConvenioService — valor hora y horas extra con cita de artículo (D34)")
class CalculoConvenioServiceTest {

    private static ConvenioCatalog catalog;
    private static CalculoConvenioService servicio;

    /**
     * Caso piloto verificado a mano contra madrid-hosteleria.json:
     * cocinero (nivel III) en restaurante de 3 tenedores (clase B), tablas 2025
     * (vigentes en 2026 por ultraactividad): salario base 1.250,91 €/mes,
     * plus convenio 191,22 € × 11 meses = 2.103,42 €/año, 14 pagas, 1.800 h.
     * valorHora = (1250,91 × 14 + 2103,42) / 1800 = 10,8979.
     */
    private static final BigDecimal SALARIO_BASE_COCINERO_B_2025 = new BigDecimal("1250.91");
    private static final BigDecimal PLUSES_ANUALES_2025 = new BigDecimal("2103.42");

    @BeforeAll
    static void arranque() {
        catalog = new ConvenioCatalog(new ObjectMapper());
        servicio = new CalculoConvenioService();
    }

    private Convenio madrid() {
        return catalog.porId("madrid-hosteleria").orElseThrow();
    }

    @Test
    @DisplayName("valor de la hora ordinaria: (base × pagas + pluses anuales) / jornada anual")
    void valorHoraOrdinaria() {
        var resultado = servicio.valorHoraOrdinaria(
                madrid(), Year.of(2026), SALARIO_BASE_COCINERO_B_2025, PLUSES_ANUALES_2025);

        assertThat(resultado).isPresent();
        assertThat(resultado.orElseThrow().valorHora())
                .isEqualByComparingTo(new BigDecimal("10.8979"));
    }

    @Test
    @DisplayName("el resultado cita jornada y pagas con su artículo del convenio")
    void valorHoraCitaArticulos() {
        var resultado = servicio.valorHoraOrdinaria(
                madrid(), Year.of(2026), SALARIO_BASE_COCINERO_B_2025, PLUSES_ANUALES_2025).orElseThrow();

        assertThat(resultado.citas())
                .anySatisfy(cita -> assertThat(cita).contains("Art. 14"))   // jornada 1800 h
                .anySatisfy(cita -> assertThat(cita).contains("Art. 26"));  // 2 pagas extraordinarias
    }

    @Test
    @DisplayName("sin jornada anual publicada no se calcula nada (no inventar)")
    void sinJornadaNoCalcula() {
        Convenio ceuta = catalog.porId("ceuta-hosteleria").orElseThrow();

        var resultado = servicio.valorHoraOrdinaria(
                ceuta, Year.of(2026), new BigDecimal("1200"), BigDecimal.ZERO);

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("horas extra: importe = horas × valor hora ordinaria (mínimo art. 35 ET)")
    void importeHorasExtra() {
        var resultado = servicio.importeHorasExtra(
                madrid(), Year.of(2026), SALARIO_BASE_COCINERO_B_2025, PLUSES_ANUALES_2025,
                new BigDecimal("5"));

        assertThat(resultado).isPresent();
        assertThat(resultado.orElseThrow().importe())
                .isEqualByComparingTo(new BigDecimal("54.49"));
        assertThat(resultado.orElseThrow().citas())
                .anySatisfy(cita -> assertThat(cita).contains("art. 35"));
    }

    @Test
    @DisplayName("si el convenio fija precio de hora extra y es mayor que la ordinaria, se usa el del convenio")
    void precioFijadoEnConvenioGanaSiEsMayor() {
        // Teruel Art. 33: precio fijo 11,40 €/h (última tabla, 2023). Con base 1.000 €
        // la hora ordinaria sale a (1000×15)/1796 = 8,3519 → gana el precio del convenio.
        Convenio teruel = catalog.porId("teruel-hosteleria").orElseThrow();

        var resultado = servicio.importeHorasExtra(
                teruel, Year.of(2026), new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("5")).orElseThrow();

        assertThat(resultado.precioHora()).isEqualByComparingTo(new BigDecimal("11.40"));
        assertThat(resultado.importe()).isEqualByComparingTo(new BigDecimal("57.00"));
        assertThat(resultado.citas())
                .anySatisfy(cita -> assertThat(cita).contains("Art. 33"));
    }

    @Test
    @DisplayName("tope de horas extra: el del convenio si lo fija, si no las 80 h del ET")
    void topeHorasExtra() {
        assertThat(servicio.topeHorasExtraAnual(madrid()).horas()).isEqualTo(80);

        Convenio teruel = catalog.porId("teruel-hosteleria").orElseThrow();
        assertThat(servicio.topeHorasExtraAnual(teruel).horas()).isEqualTo(80);
        assertThat(servicio.topeHorasExtraAnual(teruel).citas())
                .anySatisfy(cita -> assertThat(cita).contains("art. 35"));
    }
}
