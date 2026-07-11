package es.medeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.medeben.domain.convenio.Convenio;
import es.medeben.repository.ConvenioCatalog;
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
        // Notación española en la cita, no anglosajona (issue #222).
        assertThat(resultado.citas())
                .anySatisfy(cita -> assertThat(cita.texto()).contains("11,40 €/h"));
        assertThat(resultado.citas())
                .noneSatisfy(cita -> assertThat(cita.texto()).contains("11.40"));
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
        @DisplayName("una 'cantidad' de pagas negativa dejaría el total bajo 12: sospechosa, no se calcula")
        void cantidadNegativaNoCalcula() {
            Convenio c = sintetico("""
                    "jornadaAnual":{"horas":1800},"pagasExtraordinarias":{"cantidad":-3}""");

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
        @DisplayName("tope bajo clave alternativa 'topeAnualHoras' (Cuenca/Zamora) → se lee y se cita el convenio")
        void topeClaveTopeAnualHoras() {
            Convenio c = sintetico("""
                    "horasExtraordinarias":{"topeAnualHoras":80,"articulo":"Art. 41"}""");
            var tope = servicio.topeHorasExtraAnual(c, Year.of(2026));
            assertThat(tope.horas()).isEqualTo(80);
            assertThat(tope.citas()).anySatisfy(cita -> assertThat(cita.texto())
                    .contains("convenio").contains("Art. 41"));
        }

        @Test
        @DisplayName("tope bajo clave alternativa 'topeAnual' numérico (Castellón, La Rioja...) → se lee")
        void topeClaveTopeAnual() {
            Convenio c = sintetico("""
                    "horasExtraordinarias":{"topeAnual":80,"articulo":"Art. 14"}""");
            assertThat(servicio.topeHorasExtraAnual(c, Year.of(2026)).horas()).isEqualTo(80);
        }

        @Test
        @DisplayName("tope anidado 'topes.año' (Asturias) y 'topes.anio' (León) → se leen ambas grafías")
        void topeAnidado() {
            Convenio asturias = sintetico("""
                    "horasExtraordinarias":{"topes":{"dia":2,"mes":15,"año":80},"articulo":"Art. 22"}""");
            Convenio leon = sintetico("""
                    "horasExtraordinarias":{"topes":{"dia":2,"mes":15,"anio":70},"articulo":"Art. 20"}""");
            assertThat(servicio.topeHorasExtraAnual(asturias, Year.of(2026)).horas()).isEqualTo(80);
            assertThat(servicio.topeHorasExtraAnual(leon, Year.of(2026)).horas()).isEqualTo(70);
        }

        @Test
        @DisplayName("tope como texto ('rige el ET') NO se toma como número → cae al ET, cita del Estatuto")
        void topeTextualCaeAlEstatuto() {
            Convenio c = sintetico("""
                    "horasExtraordinarias":{"topeAnual":"no fijado en convenio (rige el art. 35.2 ET)"}""");
            var tope = servicio.topeHorasExtraAnual(c, Year.of(2026));
            assertThat(tope.horas()).isEqualTo(80);
            assertThat(tope.citas()).anySatisfy(cita -> assertThat(cita.texto()).contains("35.2 ET"));
        }

        @Test
        @DisplayName("convenio real (Cuenca): el tope de 80 h se cita desde el convenio, no como suelo genérico del ET")
        void topeCuencaCitaConvenio() {
            Convenio cuenca = catalog.porId("cuenca-hosteleria").orElseThrow();
            var tope = servicio.topeHorasExtraAnual(cuenca, Year.of(2026));
            assertThat(tope.horas()).isEqualTo(80);
            assertThat(tope.citas()).anySatisfy(cita -> assertThat(cita.texto()).contains("convenio"));
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

    @Nested
    @DisplayName("Recargo porcentual de la hora extra (23 convenios lo fijan así)")
    class RecargoPorcentual {

        private static final BigDecimal BASE = new BigDecimal("1300");
        private static final Year ANIO = Year.of(2026);

        /** precio hora extra == valor hora ordinaria × (1 + %/100), sin pluses. */
        private void assertRecargo(String convenioId, String factorEsperado, String pctEnCita) {
            Convenio convenio = catalog.porId(convenioId).orElseThrow();
            var vho = servicio.valorHoraOrdinaria(convenio, ANIO, BASE, BigDecimal.ZERO);
            assertThat(vho).as("valor hora de %s", convenioId).isPresent();
            BigDecimal valorHora = vho.orElseThrow().valorHora();

            var extra = servicio.importeHorasExtra(convenio, ANIO, BASE, BigDecimal.ZERO, BigDecimal.ONE)
                    .orElseThrow();
            assertThat(extra.precioHora())
                    .as("precio hora extra de %s con recargo", convenioId)
                    .isEqualByComparingTo(valorHora.multiply(new BigDecimal(factorEsperado)));
            assertThat(extra.citas())
                    .anySatisfy(c -> assertThat(c.texto()).contains(pctEnCita));
        }

        @Test
        @DisplayName("Cádiz: recargo del 75% → precio = valor hora × 1,75")
        void cadiz75() {
            assertRecargo("cadiz-hosteleria", "1.75", "75%");
        }

        @Test
        @DisplayName("Granada: recargo del 100% → la hora extra al doble")
        void granada100() {
            assertRecargo("granada-hosteleria", "2", "100%");
        }

        @Test
        @DisplayName("La Rioja (hospedaje): 'salario_real_ordinario' (masculino) también cuenta → ×2")
        void larioja100Masculino() {
            assertRecargo("larioja-hospedaje", "2", "100%");
        }

        @Test
        @DisplayName("Zaragoza (caso de Iulian): recargo del 75% → precio = valor hora × 1,75")
        void zaragoza75() {
            assertRecargo("zaragoza-hosteleria", "1.75", "75%");
        }

        @Test
        @DisplayName("Córdoba a tramos (50%/75%): se aplica el 50% como mínimo garantizado y se citan ambos")
        void cordobaTramosMinimo() {
            Convenio cordoba = catalog.porId("cordoba-hosteleria").orElseThrow();
            var vho = servicio.valorHoraOrdinaria(cordoba, ANIO, BASE, BigDecimal.ZERO);
            assertThat(vho).isPresent();
            BigDecimal valorHora = vho.orElseThrow().valorHora();

            var extra = servicio.importeHorasExtra(cordoba, ANIO, BASE, BigDecimal.ZERO, BigDecimal.ONE)
                    .orElseThrow();
            assertThat(extra.precioHora()).isEqualByComparingTo(valorHora.multiply(new BigDecimal("1.50")));
            assertThat(extra.citas()).anySatisfy(c -> {
                assertThat(c.texto()).contains("50%");
                assertThat(c.texto()).contains("75%");
            });
        }

        @Test
        @DisplayName("Cuenca 'se abona AL 175%' → ×1,75 (NO ×2,75): distinta semántica que Cantabria")
        void cuencaAbonoTotal175() {
            assertRecargo("cuenca-hosteleria", "1.75", "175%");
        }

        @Test
        @DisplayName("Cantabria 'incremento DEL 175%' → ×2,75 (recargo encima, no abono total)")
        void cantabriaRecargo175() {
            assertRecargo("cantabria-hosteleria", "2.75", "175%");
        }

        @Test
        @DisplayName("Las Palmas 'al doble' (100%) → ×2 (recargo legible que antes se ignoraba)")
        void lasPalmas100() {
            assertRecargo("laspalmas-hosteleria", "2", "100%");
        }

        @Test
        @DisplayName("A Coruña ≥25% → ×1,25 (mínimo garantizado del recargo en gallego)")
        void acoruna25Minimo() {
            assertRecargo("acoruna-hosteleria", "1.25", "25%");
        }

        @Test
        @DisplayName("Castellón: recargo implícito del 75% (fórmula 'precio hora + 75%') → ×1,75")
        void castellon75Implicito() {
            assertRecargo("castellon-hosteleria", "1.75", "75%");
        }

        @Test
        @DisplayName("Soria: recargo implícito del 75% (factor 1,75) → ×1,75")
        void soria75Implicito() {
            assertRecargo("soria-hosteleria", "1.75", "75%");
        }

        @Test
        @DisplayName("Madrid no fija recargo %: la hora extra se paga al valor ordinario (suelo del ET), sin inventar recargo")
        void madridSinRecargoQuedaEnOrdinaria() {
            var vho = servicio.valorHoraOrdinaria(madrid(), ANIO, BASE, BigDecimal.ZERO);
            BigDecimal valorHora = vho.orElseThrow().valorHora();
            var extra = servicio.importeHorasExtra(madrid(), ANIO, BASE, BigDecimal.ZERO, BigDecimal.ONE)
                    .orElseThrow();
            assertThat(extra.precioHora()).isEqualByComparingTo(valorHora);
        }
    }
}
