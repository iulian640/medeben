package es.tedeben.dto;

import es.tedeben.domain.fichaje.OrigenApunte;
import es.tedeben.domain.fichaje.TipoApunte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El motivo de una ausencia puede ser dato de salud (art. 9 RGPD): el
 * toString autogenerado de los records lo volcaría en cualquier log
 * accidental, así que ambos DTOs lo redactan.
 */
@DisplayName("DTOs de apuntes — el motivo nunca sale por toString (RGPD art. 9)")
class ApunteDtoTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 7, 8);
    private static final String MOTIVO = "migraña con aura, baja médica";

    @Test
    @DisplayName("ApunteRequest.toString redacta el motivo pero conserva el resto de campos")
    void requestRedactaMotivo() {
        var peticion = new ApunteRequest(FECHA, TipoApunte.AUSENCIA, null, MOTIVO, false);

        assertThat(peticion.toString())
                .doesNotContain(MOTIVO)
                .doesNotContain("migraña")
                .contains("<redactado>")
                .contains("AUSENCIA")
                .contains("2026-07-08");
    }

    @Test
    @DisplayName("ApunteResponse.toString redacta el motivo pero conserva el resto de campos")
    void responseRedactaMotivo() {
        var respuesta = new ApunteResponse(FECHA, TipoApunte.AUSENCIA, null, MOTIVO,
                OrigenApunte.CONFIRMADO, OffsetDateTime.parse("2026-07-08T09:00:00+02:00"));

        assertThat(respuesta.toString())
                .doesNotContain(MOTIVO)
                .doesNotContain("migraña")
                .contains("<redactado>")
                .contains("CONFIRMADO");
    }

    @Test
    @DisplayName("sin motivo, toString lo enseña como null (que depurar no engañe)")
    void sinMotivoSeVeNull() {
        var peticion = new ApunteRequest(FECHA, TipoApunte.ENTRADA, "09:00", null, false);

        assertThat(peticion.toString()).contains("motivo=null");
    }
}
