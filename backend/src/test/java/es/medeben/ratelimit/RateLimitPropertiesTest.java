package es.medeben.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitProperties — presupuestos por defecto cuando faltan en la configuración")
class RateLimitPropertiesTest {

    @Test
    @DisplayName("auth y api nulos -> se sustituyen por los presupuestos por defecto")
    void authYApiNulosSeSustituyenPorLosPresupuestosPorDefecto() {
        RateLimitProperties propiedades = new RateLimitProperties(true, false, null, null);

        assertThat(propiedades.auth().capacidad()).isEqualTo(30);
        assertThat(propiedades.auth().recargaPorMinuto()).isEqualTo(20);
        assertThat(propiedades.api().capacidad()).isEqualTo(40);
        assertThat(propiedades.api().recargaPorMinuto()).isEqualTo(120);
    }

    @Test
    @DisplayName("auth y api explícitos -> se respetan tal cual")
    void authYApiExplicitosSeRespetanTalCual() {
        RateLimitProperties.Presupuesto auth = new RateLimitProperties.Presupuesto(5, 5);
        RateLimitProperties.Presupuesto api = new RateLimitProperties.Presupuesto(50, 200);

        RateLimitProperties propiedades = new RateLimitProperties(true, true, auth, api);

        assertThat(propiedades.auth()).isEqualTo(auth);
        assertThat(propiedades.api()).isEqualTo(api);
        assertThat(propiedades.habilitado()).isTrue();
        assertThat(propiedades.confiarEnProxy()).isTrue();
    }
}
