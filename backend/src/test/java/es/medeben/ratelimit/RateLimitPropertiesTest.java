package es.medeben.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitProperties — presupuestos por defecto cuando faltan en la configuración")
class RateLimitPropertiesTest {

    @Test
    @DisplayName("presupuestos nulos -> se sustituyen por los valores por defecto")
    void authYApiNulosSeSustituyenPorLosPresupuestosPorDefecto() {
        RateLimitProperties propiedades = new RateLimitProperties(true, false, null, null, null);

        assertThat(propiedades.auth().capacidad()).isEqualTo(30);
        assertThat(propiedades.auth().recargaPorMinuto()).isEqualTo(20);
        assertThat(propiedades.api().capacidad()).isEqualTo(40);
        assertThat(propiedades.api().recargaPorMinuto()).isEqualTo(120);
        // El de informes es a propósito MUCHO más estrecho: generar un PDF
        // cuesta un año de recorrido + maquetado (anti amplificación de CPU).
        assertThat(propiedades.informes().capacidad()).isEqualTo(5);
        assertThat(propiedades.informes().recargaPorMinuto()).isEqualTo(3);
    }

    @Test
    @DisplayName("presupuestos explícitos -> se respetan tal cual")
    void authYApiExplicitosSeRespetanTalCual() {
        RateLimitProperties.Presupuesto auth = new RateLimitProperties.Presupuesto(5, 5);
        RateLimitProperties.Presupuesto api = new RateLimitProperties.Presupuesto(50, 200);
        RateLimitProperties.Presupuesto informes = new RateLimitProperties.Presupuesto(2, 1);

        RateLimitProperties propiedades = new RateLimitProperties(true, true, auth, api, informes);

        assertThat(propiedades.auth()).isEqualTo(auth);
        assertThat(propiedades.api()).isEqualTo(api);
        assertThat(propiedades.informes()).isEqualTo(informes);
        assertThat(propiedades.habilitado()).isTrue();
        assertThat(propiedades.confiarEnProxy()).isTrue();
    }
}
