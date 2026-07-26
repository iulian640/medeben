package es.medeben.service;

import es.medeben.controller.RecursoNoEncontradoException;
import es.medeben.domain.fichaje.CentroTrabajo;
import es.medeben.domain.fichaje.EstadoCentroTrabajo;
import es.medeben.repository.CentroTrabajoRepository;
import es.medeben.repository.UbicacionApunteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CentroTrabajoServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-07-26T08:00:00Z"), MADRID);
    private static final BigDecimal LAT = new BigDecimal("40.41675");
    private static final BigDecimal LON = new BigDecimal("-3.70379");

    private CentroTrabajoRepository centros;
    private UbicacionApunteRepository ubicaciones;
    private ConsentimientoUbicacionService consentimientos;
    private CentroTrabajoService servicio;

    @BeforeEach
    void arranque() {
        centros = mock(CentroTrabajoRepository.class);
        ubicaciones = mock(UbicacionApunteRepository.class);
        consentimientos = mock(ConsentimientoUbicacionService.class);
        servicio = new CentroTrabajoService(centros, ubicaciones, consentimientos, RELOJ);
        when(consentimientos.vigente(USUARIO)).thenReturn(true);
        when(centros.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("declarar sin consentimiento vigente se rechaza con 403")
    void declararSinConsentimientoSeRechaza() {
        when(consentimientos.vigente(USUARIO)).thenReturn(false);

        assertThatThrownBy(() -> servicio.declara(USUARIO, "El bar", LAT, LON))
                .isInstanceOf(ConsentimientoUbicacionRequeridoException.class);
        verify(centros, never()).save(any());
    }

    @Test
    @DisplayName("declarar guarda con el radio fijo de 150 m puesto por el servidor, no por el cliente")
    void declararGuardaConRadioFijoPuestoPorElServidor() {
        CentroTrabajo centro = servicio.declara(USUARIO, "El bar", LAT, LON);

        assertThat(centro.getRadioMetros()).isEqualTo(CentroTrabajoService.RADIO_METROS_DEFECTO);
        assertThat(centro.getEstado()).isEqualTo(EstadoCentroTrabajo.ALTA);
        assertThat(centro.getDeclaradoEn()).isEqualTo(OffsetDateTime.now(RELOJ));
    }

    @Test
    @DisplayName("vigente() es vacío cuando la última declaración es una BAJA, aunque exista un ALTA anterior")
    void vigenteEsVacioCuandoLaUltimaDeclaracionEsBaja() {
        CentroTrabajo alta = new CentroTrabajo(USUARIO, "El bar", LAT, LON, 150,
                OffsetDateTime.parse("2026-07-01T09:00:00+02:00"));
        CentroTrabajo baja = CentroTrabajo.baja(alta, OffsetDateTime.parse("2026-07-10T09:00:00+02:00"));
        when(centros.findFirstByUsuarioIdOrderByDeclaradoEnDesc(USUARIO)).thenReturn(Optional.of(baja));

        assertThat(servicio.vigente(USUARIO)).isEmpty();
    }

    @Test
    @DisplayName("cerrar sin centro vigente da 404")
    void cerrarSinCentroVigenteDa404() {
        when(centros.findFirstByUsuarioIdOrderByDeclaradoEnDesc(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cierra(USUARIO)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("cerrar inserta una fila BAJA copiando lat/lon/radio del alta que cierra")
    void cerrarInsertaFilaBajaCopiandoLatLonRadio() {
        CentroTrabajo alta = new CentroTrabajo(USUARIO, "El bar", LAT, LON, 150,
                OffsetDateTime.parse("2026-07-01T09:00:00+02:00"));
        when(centros.findFirstByUsuarioIdOrderByDeclaradoEnDesc(USUARIO)).thenReturn(Optional.of(alta));

        servicio.cierra(USUARIO);

        org.mockito.ArgumentCaptor<CentroTrabajo> captor = org.mockito.ArgumentCaptor.forClass(CentroTrabajo.class);
        verify(centros).save(captor.capture());
        CentroTrabajo baja = captor.getValue();
        assertThat(baja.getEstado()).isEqualTo(EstadoCentroTrabajo.BAJA);
        assertThat(baja.getLatitud()).isEqualByComparingTo(LAT);
        assertThat(baja.getLongitud()).isEqualByComparingTo(LON);
        assertThat(baja.getRadioMetros()).isEqualTo(150);
        assertThat(baja.getId()).isNotEqualTo(alta.getId());
    }

    @Test
    @DisplayName("purgar un centro de otro usuario, o inexistente, da 404 (mismo error)")
    void purgarDeOtroUsuarioOInexistenteDa404() {
        UUID centroId = UUID.randomUUID();
        when(centros.findById(centroId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.purga(USUARIO, centroId))
                .isInstanceOf(RecursoNoEncontradoException.class);

        CentroTrabajo deOtro = new CentroTrabajo(UUID.randomUUID(), "otro", LAT, LON, 150,
                OffsetDateTime.now(RELOJ));
        when(centros.findById(centroId)).thenReturn(Optional.of(deOtro));
        assertThatThrownBy(() -> servicio.purga(USUARIO, centroId))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("purgar con menos de 24h desde la declaración borra físicamente la fila")
    void purgarMenosDeVeinticuatroHorasBorraFisicamente() {
        CentroTrabajo reciente = new CentroTrabajo(USUARIO, "El bar", LAT, LON, 150,
                OffsetDateTime.now(RELOJ).minusHours(2));
        when(centros.findById(reciente.getId())).thenReturn(Optional.of(reciente));

        servicio.purga(USUARIO, reciente.getId());

        verify(centros).deleteById(reciente.getId());
        verify(centros, never()).save(any());
        verify(ubicaciones, never()).anulaCoordenadasDelCentro(any());
    }

    @Test
    @DisplayName("purgar con 24h o más hace tombstone (lat/lon/alias a null) y propaga a las copias congeladas")
    void purgarVeinticuatroHorasOMasHaceTombstoneYPropaga() {
        CentroTrabajo antiguo = new CentroTrabajo(USUARIO, "El bar", LAT, LON, 150,
                OffsetDateTime.now(RELOJ).minusHours(24));
        when(centros.findById(antiguo.getId())).thenReturn(Optional.of(antiguo));

        servicio.purga(USUARIO, antiguo.getId());

        verify(centros, never()).deleteById(any());
        org.mockito.ArgumentCaptor<CentroTrabajo> captor = org.mockito.ArgumentCaptor.forClass(CentroTrabajo.class);
        verify(centros).save(captor.capture());
        CentroTrabajo tombstoned = captor.getValue();
        assertThat(tombstoned.getLatitud()).isNull();
        assertThat(tombstoned.getLongitud()).isNull();
        assertThat(tombstoned.getAlias()).isNull();
        // Un ALTA tombstoned pasa a BAJA: no puede quedar "vigente" sin coordenadas.
        assertThat(tombstoned.getEstado()).isEqualTo(EstadoCentroTrabajo.BAJA);
        verify(ubicaciones).anulaCoordenadasDelCentro(antiguo.getId());
    }
}
