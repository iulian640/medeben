package es.medeben.service;

import es.medeben.domain.usuario.ConsentimientoUbicacion;
import es.medeben.repository.ConsentimientoUbicacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsentimientoUbicacionServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), MADRID);

    private ConsentimientoUbicacionRepository consentimientos;
    private ConsentimientoUbicacionService servicio;

    @BeforeEach
    void arranque() {
        consentimientos = mock(ConsentimientoUbicacionRepository.class);
        servicio = new ConsentimientoUbicacionService(consentimientos, RELOJ);
        when(consentimientos.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("aceptar registra la fecha y la versión del texto, con el hash del texto canónico")
    void aceptarRegistraLaFechaYLaVersionDelTexto() {
        ConsentimientoUbicacion resultado = servicio.acepta(USUARIO, "1.0");

        assertThat(resultado.getUsuarioId()).isEqualTo(USUARIO);
        assertThat(resultado.getVersionTexto()).isEqualTo("1.0");
        assertThat(resultado.getTextoSha256())
                .isEqualTo(Sha256.hex(ConsentimientoUbicacionTexto.TEXTO_V1_0));
        assertThat(resultado.getAceptadoEn()).isEqualTo(OffsetDateTime.now(RELOJ));
        assertThat(resultado.isVigente()).isTrue();
    }

    @Test
    @DisplayName("aceptar con una versión de texto desconocida se rechaza")
    void aceptarConVersionDesconocidaSeRechaza() {
        assertThatThrownBy(() -> servicio.acepta(USUARIO, "9.9"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("revocar rellena revocadoEn sin borrar la fila")
    void revocarRellenaRevocadoEnSinBorrarLaFila() {
        ConsentimientoUbicacion vigente = new ConsentimientoUbicacion(USUARIO, "1.0",
                Sha256.hex(ConsentimientoUbicacionTexto.TEXTO_V1_0),
                OffsetDateTime.parse("2026-07-20T09:00:00+02:00"));
        when(consentimientos.findFirstByUsuarioIdOrderByAceptadoEnDesc(USUARIO))
                .thenReturn(Optional.of(vigente));

        servicio.revoca(USUARIO);

        assertThat(vigente.getRevocadoEn()).isEqualTo(OffsetDateTime.now(RELOJ));
        assertThat(vigente.isVigente()).isFalse();
        verify(consentimientos).save(vigente);
    }

    @Test
    @DisplayName("revocar sin consentimiento vigente no hace nada (idempotente)")
    void revocarSinConsentimientoVigenteNoHaceNada() {
        when(consentimientos.findFirstByUsuarioIdOrderByAceptadoEnDesc(USUARIO)).thenReturn(Optional.empty());

        servicio.revoca(USUARIO);

        verify(consentimientos, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("re-aceptar tras revocar crea fila nueva, no reabre la vieja")
    void reAceptarTrasRevocarCreaFilaNuevaNoReabreLaVieja() {
        ConsentimientoUbicacion resultado = servicio.acepta(USUARIO, "1.0");

        assertThat(resultado.isNew()).isTrue();
        assertThat(resultado.getRevocadoEn()).isNull();
    }

    @Test
    @DisplayName("vigente() es true cuando la última fila no está revocada")
    void vigenteEsTrueCuandoLaUltimaFilaNoEstaRevocada() {
        ConsentimientoUbicacion fila = new ConsentimientoUbicacion(USUARIO, "1.0", "hash",
                OffsetDateTime.now(RELOJ));
        when(consentimientos.findFirstByUsuarioIdOrderByAceptadoEnDesc(USUARIO)).thenReturn(Optional.of(fila));

        assertThat(servicio.vigente(USUARIO)).isTrue();
    }

    @Test
    @DisplayName("vigente() es false cuando la última fila está revocada, o no hay ninguna")
    void vigenteEsFalseCuandoLaUltimaFilaEstaRevocadaONoHayNinguna() {
        when(consentimientos.findFirstByUsuarioIdOrderByAceptadoEnDesc(USUARIO)).thenReturn(Optional.empty());
        assertThat(servicio.vigente(USUARIO)).isFalse();

        ConsentimientoUbicacion revocado = new ConsentimientoUbicacion(USUARIO, "1.0", "hash",
                OffsetDateTime.now(RELOJ).minusDays(1));
        revocado.revoca(OffsetDateTime.now(RELOJ));
        when(consentimientos.findFirstByUsuarioIdOrderByAceptadoEnDesc(USUARIO)).thenReturn(Optional.of(revocado));
        assertThat(servicio.vigente(USUARIO)).isFalse();
    }
}
