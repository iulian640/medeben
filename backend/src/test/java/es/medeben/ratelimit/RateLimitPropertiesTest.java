package es.medeben.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitProperties — presupuestos por defecto cuando faltan en la configuración")
class RateLimitPropertiesTest {

    @Test
    @DisplayName("presupuestos nulos -> se sustituyen por los valores por defecto")
    void authYApiNulosSeSustituyenPorLosPresupuestosPorDefecto() {
        RateLimitProperties propiedades = new RateLimitProperties(true, false, null, null, null, null, null, null);

        // Proxies de confianza por defecto: loopback + rangos privados (Docker).
        assertThat(propiedades.proxiesDeConfianza())
                .containsExactly("127.0.0.1/32", "::1", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16");
        assertThat(propiedades.auth().capacidad()).isEqualTo(30);
        assertThat(propiedades.auth().recargaPorMinuto()).isEqualTo(20);
        // El de refresh es más ancho que el de auth (B4): tráfico sostenido
        // legítimo de toda una plantilla tras una misma IP, ~4/hora por usuario.
        assertThat(propiedades.refresh().capacidad()).isEqualTo(60);
        assertThat(propiedades.refresh().recargaPorMinuto()).isEqualTo(40);
        // El de registro es a propósito MÁS ESTRECHO que el de auth (auditoría):
        // el 409 de email duplicado permite enumerar cuentas.
        assertThat(propiedades.registro().capacidad()).isEqualTo(5);
        assertThat(propiedades.registro().recargaPorMinuto()).isEqualTo(2);
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
        RateLimitProperties.Presupuesto refresh = new RateLimitProperties.Presupuesto(9, 9);
        RateLimitProperties.Presupuesto registro = new RateLimitProperties.Presupuesto(3, 3);
        RateLimitProperties.Presupuesto api = new RateLimitProperties.Presupuesto(50, 200);
        RateLimitProperties.Presupuesto informes = new RateLimitProperties.Presupuesto(2, 1);

        RateLimitProperties propiedades = new RateLimitProperties(
                true, true, java.util.List.of("203.0.113.7/32"), auth, refresh, registro, api, informes);

        assertThat(propiedades.proxiesDeConfianza()).containsExactly("203.0.113.7/32");
        assertThat(propiedades.auth()).isEqualTo(auth);
        assertThat(propiedades.refresh()).isEqualTo(refresh);
        assertThat(propiedades.registro()).isEqualTo(registro);
        assertThat(propiedades.api()).isEqualTo(api);
        assertThat(propiedades.informes()).isEqualTo(informes);
        assertThat(propiedades.habilitado()).isTrue();
        assertThat(propiedades.confiarEnProxy()).isTrue();
    }
}
