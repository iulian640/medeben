package es.medeben.domain.fichaje;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Haversine puro, sin Spring: es donde vive el riesgo real de la fórmula del
 * veredicto (§7 síntesis). NUMERIC(8,5) es la resolución real del dato.
 */
class DistanciaTest {

    @Test
    @DisplayName("distancia conocida entre dos puntos: Puerta del Sol y Callao (~1,2 km)")
    void distanciaConocidaEntreDosPuntos() {
        // Puerta del Sol, Madrid
        BigDecimal lat1 = new BigDecimal("40.41675");
        BigDecimal lon1 = new BigDecimal("-3.70379");
        // Plaza de Callao, Madrid
        BigDecimal lat2 = new BigDecimal("40.42017");
        BigDecimal lon2 = new BigDecimal("-3.70536");

        int metros = Distancia.metrosEntre(lat1, lon1, lat2, lon2);

        // Distancia real ~410 m; margen amplio porque no perseguimos precisión
        // de laboratorio, solo que la fórmula esté bien implementada.
        assertThat((double) metros).isCloseTo(410.0, within(60.0));
    }

    @Test
    @DisplayName("un grado de latitud son aproximadamente 111,2 km")
    void unGradoDeLatitudSonOnceOnceKilometros() {
        BigDecimal lat1 = new BigDecimal("40.00000");
        BigDecimal lon = new BigDecimal("-3.00000");
        BigDecimal lat2 = new BigDecimal("41.00000");

        int metros = Distancia.metrosEntre(lat1, lon, lat2, lon);

        assertThat((double) metros).isCloseTo(111_200.0, within(2_000.0));
    }

    @Test
    @DisplayName("la distancia de un punto consigo mismo es cero")
    void distanciaCeroConsigoMismo() {
        BigDecimal lat = new BigDecimal("40.41675");
        BigDecimal lon = new BigDecimal("-3.70379");

        assertThat(Distancia.metrosEntre(lat, lon, lat, lon)).isZero();
    }

    @Test
    @DisplayName("la distancia es simétrica: A→B igual que B→A")
    void laDistanciaEsSimetrica() {
        BigDecimal lat1 = new BigDecimal("40.41675");
        BigDecimal lon1 = new BigDecimal("-3.70379");
        BigDecimal lat2 = new BigDecimal("41.38506");
        BigDecimal lon2 = new BigDecimal("2.17340");

        int aB = Distancia.metrosEntre(lat1, lon1, lat2, lon2);
        int bA = Distancia.metrosEntre(lat2, lon2, lat1, lon1);

        assertThat(aB).isEqualTo(bA);
    }

    @Test
    @DisplayName("no explota en el ecuador ni al cruzar el antimeridiano")
    void noExplotaEnElEcuadorNiEnElAntimeridiano() {
        BigDecimal cero = BigDecimal.ZERO;
        assertThat(Distancia.metrosEntre(cero, cero, cero, new BigDecimal("0.001"))).isGreaterThan(0);

        BigDecimal lat = new BigDecimal("10.00000");
        BigDecimal cercaDe180 = new BigDecimal("179.99900");
        BigDecimal otroLado = new BigDecimal("-179.99900");
        int metros = Distancia.metrosEntre(lat, cercaDe180, lat, otroLado);

        // Cruzando el antimeridiano, dos puntos casi pegados deben salir CERCA
        // (no dar la vuelta al mundo por el otro lado): unos cientos de metros.
        assertThat(metros).isLessThan(500);
    }
}
