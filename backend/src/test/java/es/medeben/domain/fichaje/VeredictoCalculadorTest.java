package es.medeben.domain.fichaje;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fórmula del veredicto (síntesis §7), con la banda al 95 % (FACTOR_SIGMA = 2,
 * corrección vinculante del contrato §Backend): accuracy de Android es radio
 * de confianza al 68 % (1 sigma), no cota dura, así que se dobla para no
 * sobre-afirmar lo que un perito tumbaría en una frase.
 */
class VeredictoCalculadorTest {

    @Test
    @DisplayName("dentro cuando distancia + 2×precisión cabe en el radio")
    void dentroCuandoDistanciaMasDosVecesPrecisionCabeEnElRadio() {
        // d=20, p=30, r=150 → 20 + 2*30 = 80 <= 150
        assertThat(VeredictoCalculador.calcula(20, 30, 150)).isEqualTo(VeredictoUbicacion.DENTRO);
    }

    @Test
    @DisplayName("fuera cuando distancia - 2×precisión supera el radio")
    void fueraCuandoDistanciaMenosDosVecesPrecisionSuperaElRadio() {
        // d=3400, p=60, r=150 → 3400 - 120 = 3280 > 150
        assertThat(VeredictoCalculador.calcula(3400, 60, 150)).isEqualTo(VeredictoUbicacion.FUERA);
    }

    @Test
    @DisplayName("no concluyente cuando la banda de error (factor 2) cruza el borde — el caso que más importa")
    void noConcluyenteCuandoLaBandaDeErrorCruzaElBorde() {
        // d=140, p=30, r=150 → d+2p=200>150 (no DENTRO) y d-2p=80<=150 (no FUERA)
        assertThat(VeredictoCalculador.calcula(140, 30, 150)).isEqualTo(VeredictoUbicacion.NO_CONCLUYENTE);
    }

    @Test
    @DisplayName("precisión peor de 200 m es no concluyente sea cual sea la distancia (guarda sobre p cruda)")
    void precisionPeorDeDoscientosMetrosEsNoConcluyenteSeaCualSeaLaDistancia() {
        // d=0 (justo encima del centro) pero p=201: la guarda corta antes de mirar d.
        assertThat(VeredictoCalculador.calcula(0, 201, 150)).isEqualTo(VeredictoUbicacion.NO_CONCLUYENTE);
    }

    @Test
    @DisplayName("borde exacto: distancia + 2×precisión == radio es DENTRO (cierre por <=)")
    void bordeExactoDeDentro() {
        // d=90, p=30, r=150 → 90 + 60 = 150 == 150
        assertThat(VeredictoCalculador.calcula(90, 30, 150)).isEqualTo(VeredictoUbicacion.DENTRO);
    }

    @Test
    @DisplayName("borde exacto: distancia - 2×precisión == radio NO es fuera (el corte es estrictamente mayor)")
    void bordeExactoDeFueraNoEsFuera() {
        // d=450, p=150, r=150 → 450 - 300 = 150, no > 150 → no concluyente
        assertThat(VeredictoCalculador.calcula(450, 150, 150)).isEqualTo(VeredictoUbicacion.NO_CONCLUYENTE);
    }

    @ParameterizedTest(name = "d={0}, p={1}, r={2} → {3}")
    @CsvSource({
            "20, 30, 150, DENTRO",
            "3400, 60, 150, FUERA",
            "140, 30, 150, NO_CONCLUYENTE",
            "0, 500, 150, NO_CONCLUYENTE",
    })
    @DisplayName("tabla completa de la fórmula (§7 de la síntesis, factor 2)")
    void tablaCompleta(int distancia, int precision, int radio, VeredictoUbicacion esperado) {
        assertThat(VeredictoCalculador.calcula(distancia, precision, radio)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("el factor sigma es una constante nombrada y vale 2 (documentado, recomputable por un tercero)")
    void elFactorSigmaEsDos() {
        assertThat(VeredictoCalculador.FACTOR_SIGMA).isEqualTo(2);
    }

    @Test
    @DisplayName("la guarda de precisión máxima está en 200 m")
    void laGuardaDePrecisionMaximaEstaEnDoscientos() {
        assertThat(VeredictoCalculador.PRECISION_MAXIMA_METROS).isEqualTo(200);
    }
}
