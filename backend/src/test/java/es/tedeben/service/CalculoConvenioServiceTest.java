package es.tedeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.tedeben.domain.convenio.Convenio;
import es.tedeben.repository.ConvenioCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
    @DisplayName("el resultado expone el desglose: base, mensualidades, pluses y jornada (para explicar la cuenta)")
    void valorHoraExponeDesglose() {
        var resultado = servicio.valorHoraOrdinaria(
                madrid(), Year.of(2026), SALARIO_BASE_COCINERO_B_2025, PLUSES_ANUALES_2025).orElseThrow();

        assertThat(resultado.salarioBaseMensual()).isEqualByComparingTo(SALARIO_BASE_COCINERO_B_2025);
        assertThat(resultado.mensualidades()).isEqualByComparingTo(new BigDecimal("14"));
        assertThat(resultado.plusesAnuales()).isEqualByComparingTo(PLUSES_ANUALES_2025);
        assertThat(resultado.divisorHoras()).isEqualByComparingTo(new BigDecimal("1800"));
        // Madrid divide por su jornada anual, no por un divisor explícito del convenio.
        assertThat(resultado.esDivisorExplicito()).isFalse();
    }

    @Test
    @DisplayName("el resultado cita jornada y pagas con su artículo del convenio")
    void valorHoraCitaArticulos() {
        var resultado = servicio.valorHoraOrdinaria(
                madrid(), Year.of(2026), SALARIO_BASE_COCINERO_B_2025, PLUSES_ANUALES_2025).orElseThrow();

        assertThat(resultado.citas())
                .anySatisfy(cita -> assertThat(cita.texto()).contains("Art. 14"))   // jornada 1800 h
                .anySatisfy(cita -> assertThat(cita.texto()).contains("Art. 26"));  // 2 pagas extraordinarias
    }

    @Test
    @DisplayName("las citas llevan enlace: al boletín oficial las del convenio, al BOE las del ET")
    void citasConEnlace() {
        var valorHora = servicio.valorHoraOrdinaria(
                madrid(), Year.of(2026), SALARIO_BASE_COCINERO_B_2025, PLUSES_ANUALES_2025).orElseThrow();
        assertThat(valorHora.citas())
                .allSatisfy(c -> assertThat(c.url()).contains("bocm.es"));

        var horasExtra = servicio.importeHorasExtra(
                madrid(), Year.of(2026), SALARIO_BASE_COCINERO_B_2025, PLUSES_ANUALES_2025,
                new BigDecimal("5")).orElseThrow();
        assertThat(horasExtra.citas())
                .anySatisfy(c -> assertThat(c.url()).isEqualTo(Cita.URL_ESTATUTO_TRABAJADORES));
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
                .anySatisfy(cita -> assertThat(cita.texto()).contains("art. 35"));
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
                .anySatisfy(cita -> assertThat(cita.texto()).contains("Art. 33"));
    }

    @Test
    @DisplayName("tope de horas extra: el del convenio si lo fija, si no las 80 h del ET")
    void topeHorasExtra() {
        assertThat(servicio.topeHorasExtraAnual(madrid(), Year.of(2026)).horas()).isEqualTo(80);

        Convenio teruel = catalog.porId("teruel-hosteleria").orElseThrow();
        assertThat(servicio.topeHorasExtraAnual(teruel, Year.of(2026)).horas()).isEqualTo(80);
        assertThat(servicio.topeHorasExtraAnual(teruel, Year.of(2026)).citas())
                .anySatisfy(cita -> assertThat(cita.texto()).contains("art. 35"));
    }

    @Test
    @DisplayName("una paga de cuantía fija no infla el multiplicador: gana mensualidadesEquivalentes")
    void pagaFijaNoInflaMultiplicador() {
        // Alicante declara cantidad=3 pero la de octubre es de cuantía FIJA:
        // mensualidadesEquivalentes=14 debe ganar → (1200 × 14) / 1796.63 = 9,3508.
        Convenio alicante = catalog.porId("alicante-hosteleria").orElseThrow();

        var resultado = servicio.valorHoraOrdinaria(
                alicante, Year.of(2026), new BigDecimal("1200"), BigDecimal.ZERO).orElseThrow();

        assertThat(resultado.valorHora()).isEqualByComparingTo(new BigDecimal("9.3508"));
        assertThat(resultado.citas()).anySatisfy(cita -> assertThat(cita.texto()).contains("14 mensualidades"));
    }

    @Test
    @DisplayName("convenios con nodo 'pagas' alternativo también calculan (A Coruña, 15 mensualidades)")
    void nodoPagasAlternativo() {
        Convenio acoruna = catalog.porId("acoruna-hosteleria").orElseThrow();

        var resultado = servicio.valorHoraOrdinaria(
                acoruna, Year.of(2026), new BigDecimal("1200"), BigDecimal.ZERO);

        assertThat(resultado).isPresent();
        assertThat(resultado.orElseThrow().citas())
                .anySatisfy(cita -> assertThat(cita.texto()).contains("15 mensualidades"));
    }

    @Nested
    @DisplayName("casos límite y datos sospechosos")
    class CasosLimite {

        private final ObjectMapper mapper = new ObjectMapper();

        private Convenio sintetico(String bloques) {
            try {
                return Convenio.desdeJson("x-hosteleria.json", mapper.readTree("""
                        {"id":"x-hosteleria","nombre":"Sintético","ambitoFuncional":"hosteleria",
                         "ambitoTerritorial":{"tipo":"provincial"},"vigencia":{"desde":"2025-01-01"},"""
                        + bloques + "}"));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }

        @Test
        @DisplayName("un total de pagas menor que 12 es sospechoso: no se calcula")
        void totalPagasMenorQue12NoCalcula() {
            Convenio c = sintetico("""
                    "jornadaAnual":{"horas":1800},"pagasExtraordinarias":{"total":3}""");

            assertThat(servicio.valorHoraOrdinaria(c, Year.of(2026), new BigDecimal("1200"), BigDecimal.ZERO))
                    .isEmpty();
        }

        @Test
        @DisplayName("jornada anual 0 es una errata: no se calcula (nada de dividir por cero)")
        void jornadaCeroNoCalcula() {
            Convenio c = sintetico("""
                    "jornadaAnual":{"horas":0},"pagasExtraordinarias":{"cantidad":2}""");

            assertThat(servicio.valorHoraOrdinaria(c, Year.of(2026), new BigDecimal("1200"), BigDecimal.ZERO))
                    .isEmpty();
        }

        @Test
        @DisplayName("si el precio del convenio queda por debajo de la hora ordinaria, aplica el suelo del ET")
        void precioConvenioBajoElSueloNoSeUsa() {
            // valorHora = (1200 × 14) / 1680 = 10.0000 > importe convenio 5 €/h.
            Convenio c = sintetico("""
                    "jornadaAnual":{"horas":1680},"pagasExtraordinarias":{"cantidad":2},
                    "horasExtraordinarias":{"importe":5}""");

            var resultado = servicio.importeHorasExtra(
                    c, Year.of(2026), new BigDecimal("1200"), BigDecimal.ZERO, new BigDecimal("5")).orElseThrow();

            assertThat(resultado.precioHora()).isEqualByComparingTo(new BigDecimal("10.0000"));
            assertThat(resultado.importe()).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("tope de horas extra propio del convenio distinto de 80")
        void topePropioDelConvenio() {
            Convenio c = sintetico("""
                    "horasExtraordinarias":{"topeHorasExtraAnual":60,"articulo":"Art. 99"}""");

            var tope = servicio.topeHorasExtraAnual(c, Year.of(2026));

            assertThat(tope.horas()).isEqualTo(60);
            assertThat(tope.citas()).anySatisfy(cita -> assertThat(cita.texto()).contains("Art. 99"));
        }

        @Test
        @DisplayName("las entradas del trabajador se validan: salario ≤ 0, pluses < 0, horas < 0")
        void entradasInvalidas() {
            Convenio madrid = catalog.porId("madrid-hosteleria").orElseThrow();

            assertThatIllegalArgumentException().isThrownBy(() ->
                    servicio.valorHoraOrdinaria(madrid, Year.of(2026), BigDecimal.ZERO, BigDecimal.ZERO));
            assertThatIllegalArgumentException().isThrownBy(() ->
                    servicio.valorHoraOrdinaria(madrid, Year.of(2026), new BigDecimal("1200"), new BigDecimal("-1")));
            assertThatIllegalArgumentException().isThrownBy(() ->
                    servicio.importeHorasExtra(madrid, Year.of(2026), new BigDecimal("1200"), BigDecimal.ZERO,
                            new BigDecimal("-1")));
        }
    }
}
