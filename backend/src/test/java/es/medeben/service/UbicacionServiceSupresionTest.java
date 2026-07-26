package es.medeben.service;

import es.medeben.controller.RecursoNoEncontradoException;
import es.medeben.domain.fichaje.UbicacionApunte;
import es.medeben.domain.fichaje.VeredictoUbicacion;
import es.medeben.repository.ApunteLecturaRepository;
import es.medeben.repository.CentroTrabajoRepository;
import es.medeben.repository.UbicacionApunteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Supresión granular y total (art. 17, contrato §Backend punto 7): NO exigen
 * consentimiento vigente como primer filtro (ver javadoc de
 * {@code UbicacionService}) — es el ejercicio del derecho de supresión, debe
 * seguir disponible aunque el usuario haya revocado el consentimiento.
 */
class UbicacionServiceSupresionTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID OTRO_USUARIO = UUID.randomUUID();
    private static final UUID APUNTE_ID = UUID.randomUUID();
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-07-26T08:00:00Z"), ZoneId.of("Europe/Madrid"));

    private ApunteLecturaRepository apuntes;
    private CentroTrabajoRepository centros;
    private UbicacionApunteRepository ubicaciones;
    private ConsentimientoUbicacionService consentimientos;
    private UbicacionService servicio;

    @BeforeEach
    void arranque() {
        apuntes = mock(ApunteLecturaRepository.class);
        centros = mock(CentroTrabajoRepository.class);
        ubicaciones = mock(UbicacionApunteRepository.class);
        consentimientos = mock(ConsentimientoUbicacionService.class);
        servicio = new UbicacionService(apuntes, centros, ubicaciones, consentimientos, RELOJ);
        when(ubicaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Ningún test de supresión debería consultar el consentimiento vigente.
        when(consentimientos.vigente(any())).thenThrow(
                new AssertionError("la supresión no debe depender del consentimiento vigente"));
    }

    private static UbicacionApunte ubicacionDe(UUID usuarioId) {
        return new UbicacionApunte(APUNTE_ID, usuarioId, LocalDate.of(2026, 7, 26),
                new BigDecimal("40.41675"), new BigDecimal("-3.70379"), 20, UUID.randomUUID(),
                new BigDecimal("40.41675"), new BigDecimal("-3.70379"), 150, 10,
                VeredictoUbicacion.DENTRO, OffsetDateTime.parse("2026-07-26T10:00:00+02:00"));
    }

    @Test
    @DisplayName("suprimir una ubicación inexistente da 404")
    void suprimirInexistenteDa404() {
        when(ubicaciones.findById(APUNTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.suprime(USUARIO, APUNTE_ID))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("suprimir la ubicación de otro usuario da el mismo error que si no existiera")
    void suprimirDeOtroUsuarioDaElMismoError() {
        when(ubicaciones.findById(APUNTE_ID)).thenReturn(Optional.of(ubicacionDe(OTRO_USUARIO)));

        assertThatThrownBy(() -> servicio.suprime(USUARIO, APUNTE_ID))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("suprimir anula coordenadas y distancia; el veredicto pasa a SUPRIMIDA")
    void suprimirAnulaCoordenadasYMarcaVeredicto() {
        UbicacionApunte propia = ubicacionDe(USUARIO);
        when(ubicaciones.findById(APUNTE_ID)).thenReturn(Optional.of(propia));

        servicio.suprime(USUARIO, APUNTE_ID);

        assertThat(propia.getLatitud()).isNull();
        assertThat(propia.getLongitud()).isNull();
        assertThat(propia.getDistanciaMetros()).isNull();
        assertThat(propia.getVeredicto()).isEqualTo(VeredictoUbicacion.SUPRIMIDA);
        // precisión y centro_radio se conservan: son lo que el veredicto necesita para leerse.
        assertThat(propia.getPrecisionMetros()).isEqualTo(20);
        assertThat(propia.getCentroRadio()).isEqualTo(150);
        verify(ubicaciones).save(propia);
    }

    @Test
    @DisplayName("borrar todas las ubicaciones del usuario delega en el repositorio")
    void borrarTodasDelegaEnElRepositorio() {
        servicio.borraTodas(USUARIO);

        verify(ubicaciones).deleteByUsuarioId(USUARIO);
        verify(ubicaciones, never()).save(any());
    }
}
