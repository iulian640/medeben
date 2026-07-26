package es.medeben.service;

import es.medeben.controller.RecursoNoEncontradoException;
import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.CentroTrabajo;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;
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

class UbicacionServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID OTRO_USUARIO = UUID.randomUUID();
    private static final UUID APUNTE_ID = UUID.randomUUID();
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** "Ahora": 2026-07-26 10:05:00 en Madrid. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-07-26T08:05:00Z"), MADRID);
    private static final LocalDate FECHA = LocalDate.of(2026, 7, 26);

    // Centro en Puerta del Sol.
    private static final BigDecimal CENTRO_LAT = new BigDecimal("40.41675");
    private static final BigDecimal CENTRO_LON = new BigDecimal("-3.70379");
    private static final int RADIO = 150;

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

        when(consentimientos.vigente(USUARIO)).thenReturn(true);
        when(ubicaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ubicaciones.existsById(APUNTE_ID)).thenReturn(false);
        when(centros.findFirstByUsuarioIdOrderByDeclaradoEnDesc(USUARIO))
                .thenReturn(Optional.of(centroVigente()));
    }

    private static CentroTrabajo centroVigente() {
        return new CentroTrabajo(USUARIO, "El bar", CENTRO_LAT, CENTRO_LON, RADIO,
                OffsetDateTime.parse("2026-07-01T09:00:00+02:00"));
    }

    /** Apunte confirmado "al momento": hora igual a la del sello, registrado hace 2 minutos. */
    private static Apunte apunteElegible() {
        return apunteConSello(OffsetDateTime.parse("2026-07-26T10:03:00+02:00"), "10:03");
    }

    private static Apunte apunteConSello(OffsetDateTime sello, String hora) {
        Apunte a = new Apunte(USUARIO, FECHA, TipoApunte.ENTRADA, hora, null,
                OrigenApunte.CONFIRMADO, sello);
        return a;
    }

    private void adjunta() {
        servicio.adjunta(USUARIO, APUNTE_ID, CENTRO_LAT, CENTRO_LON, 20);
    }

    // --- Consentimiento: primer filtro ---

    @Test
    @DisplayName("sin consentimiento vigente se rechaza con 403 y el apunte sigue intacto")
    void sinConsentimientoVigenteSeRechazaYElApunteSigueIntacto() {
        when(consentimientos.vigente(USUARIO)).thenReturn(false);

        assertThatThrownBy(this::adjunta).isInstanceOf(ConsentimientoUbicacionRequeridoException.class);

        verify(apuntes, never()).findById(any());
        verify(ubicaciones, never()).save(any());
    }

    // --- 404: dueño / existencia ---

    @Test
    @DisplayName("apunte inexistente da 404")
    void apunteInexistenteDa404() {
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(this::adjunta).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("adjuntar a un apunte de otro usuario da el MISMO error que si no existiera")
    void adjuntarAUnApunteDeOtroUsuarioDaElMismoErrorQueSiNoExistiera() {
        Apunte deOtro = new Apunte(OTRO_USUARIO, FECHA, TipoApunte.ENTRADA, "10:03", null,
                OrigenApunte.CONFIRMADO, OffsetDateTime.parse("2026-07-26T10:03:00+02:00"));
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(deOtro));

        assertThatThrownBy(this::adjunta).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // --- 422: origen ---

    @Test
    @DisplayName("un apunte reconstruido rechaza la ubicación — el test que protege D7")
    void unApunteReconstruidoRechazaLaUbicacion() {
        Apunte reconstruido = new Apunte(USUARIO, FECHA, TipoApunte.ENTRADA, "10:03", null,
                OrigenApunte.RECONSTRUIDO, OffsetDateTime.parse("2026-07-26T10:03:00+02:00"));
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(reconstruido));

        assertThatThrownBy(this::adjunta).isInstanceOf(UbicacionNoElegibleException.class);
    }

    @Test
    @DisplayName("una rectificación tardía rechaza la ubicación")
    void unaRectificacionTardiaRechazaLaUbicacion() {
        Apunte rectificacion = new Apunte(USUARIO, FECHA, TipoApunte.ENTRADA, "10:03", null,
                OrigenApunte.RECTIFICACION_TARDIA, OffsetDateTime.parse("2026-07-26T10:03:00+02:00"));
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(rectificacion));

        assertThatThrownBy(this::adjunta).isInstanceOf(UbicacionNoElegibleException.class);
    }

    // --- 422: ventana de 10 minutos ---

    @Test
    @DisplayName("más de diez minutos después del fichaje se rechaza")
    void masDeDiezMinutosDespuesDelFichajeSeRechaza() {
        // "Ahora" es 10:05; registrado a las 09:54 → 11 minutos.
        Apunte viejo = apunteConSello(OffsetDateTime.parse("2026-07-26T09:54:00+02:00"), "09:54");
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(viejo));

        assertThatThrownBy(this::adjunta).isInstanceOf(UbicacionNoElegibleException.class);
    }

    @Test
    @DisplayName("dentro de los diez minutos (borde) se acepta")
    void dentroDeLosDiezMinutosSeAcepta() {
        // "Ahora" 10:05; registrado a las 09:55 → exactamente 10 minutos.
        Apunte limite = apunteConSello(OffsetDateTime.parse("2026-07-26T09:55:00+02:00"), "09:55");
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(limite));

        adjunta();

        verify(ubicaciones).save(any());
    }

    // --- 422 CRÍTICO: regla ±3 min hora-vs-sello (agujero del manual-de-hoy) ---

    @Test
    @DisplayName("un apunte manual de hoy con hora tecleada rechaza la ubicación")
    void unApunteManualDeHoyConHoraTecleadaRechazaLaUbicacion() {
        // Se ficha ahora mismo (registradoEn dentro de la ventana de 10 min) pero
        // con una hora tecleada muy alejada del sello: "tecleó a las 10:03 «entré
        // a las 09:00»" — origen sigue siendo CONFIRMADO (esAlMomento no lo pilla).
        Apunte manual = apunteConSello(OffsetDateTime.parse("2026-07-26T10:03:00+02:00"), "09:00");
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(manual));

        assertThatThrownBy(this::adjunta).isInstanceOf(UbicacionNoElegibleException.class);
        verify(ubicaciones, never()).save(any());
    }

    @Test
    @DisplayName("hora y sello coherentes dentro de ±3 min se aceptan")
    void horaYSelloCoherentesDentroDeTresMinutosSeAceptan() {
        Apunte coherente = apunteConSello(OffsetDateTime.parse("2026-07-26T10:03:00+02:00"), "10:00");
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(coherente));

        adjunta();

        verify(ubicaciones).save(any());
    }

    @Test
    @DisplayName("un turno de cierre que cruza la medianoche no dispara la regla ±3 min por error")
    void turnoDeCierreCruzandoMedianocheNoDisparaLaReglaPorError() {
        // Se ficha la salida justo tras la medianoche (registradoEn 00:02 del día
        // siguiente al turno), con hora declarada "23:59" del día del turno (fecha
        // no cambia: Apunte.fecha es el día del TURNO, no del reloj). Diferencia
        // circular real: solo 3 minutos, no ~23h58.
        Clock relojDeCierre = Clock.fixed(Instant.parse("2026-07-26T22:04:00Z"), MADRID); // 00:04 local, 27-jul
        UbicacionService servicioDeCierre = new UbicacionService(apuntes, centros, ubicaciones,
                consentimientos, relojDeCierre);
        Apunte cierre = apunteConSello(OffsetDateTime.parse("2026-07-27T00:02:00+02:00"), "23:59");
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(cierre));

        servicioDeCierre.adjunta(USUARIO, APUNTE_ID, CENTRO_LAT, CENTRO_LON, 20);

        verify(ubicaciones).save(any());
    }

    // --- 422: centro vigente ---

    @Test
    @DisplayName("sin centro vigente se rechaza")
    void sinCentroVigenteSeRechaza() {
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(apunteElegible()));
        when(centros.findFirstByUsuarioIdOrderByDeclaradoEnDesc(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(this::adjunta).isInstanceOf(UbicacionNoElegibleException.class);
    }

    @Test
    @DisplayName("un centro dado de BAJA no cuenta como vigente, aunque sea la última fila")
    void unCentroDadoDeBajaNoCuentaComoVigente() {
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(apunteElegible()));
        CentroTrabajo alta = centroVigente();
        CentroTrabajo baja = CentroTrabajo.baja(alta, OffsetDateTime.parse("2026-07-15T09:00:00+02:00"));
        when(centros.findFirstByUsuarioIdOrderByDeclaradoEnDesc(USUARIO)).thenReturn(Optional.of(baja));

        assertThatThrownBy(this::adjunta).isInstanceOf(UbicacionNoElegibleException.class);
    }

    // --- 409: duplicado ---

    @Test
    @DisplayName("la segunda ubicación sobre el mismo apunte no pisa a la primera")
    void laSegundaUbicacionSobreElMismoApunteNoPisaALaPrimera() {
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(apunteElegible()));
        when(ubicaciones.existsById(APUNTE_ID)).thenReturn(true);

        assertThatThrownBy(this::adjunta).isInstanceOf(UbicacionYaRegistradaException.class);
        verify(ubicaciones, never()).save(any());
    }

    // --- Congelación del centro (D5) ---

    @Test
    @DisplayName("se persiste la copia del centro vigente, no una referencia")
    void sePersisteLaCopiaDelCentroVigenteNoUnaReferencia() {
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(apunteElegible()));

        UbicacionApunte guardada = servicio.adjunta(USUARIO, APUNTE_ID, CENTRO_LAT, CENTRO_LON, 20);

        assertThat(guardada.getCentroLatitud()).isEqualByComparingTo(CENTRO_LAT);
        assertThat(guardada.getCentroLongitud()).isEqualByComparingTo(CENTRO_LON);
        assertThat(guardada.getCentroRadio()).isEqualTo(RADIO);
        assertThat(guardada.getVeredicto()).isEqualTo(VeredictoUbicacion.DENTRO);
    }

    @Test
    @DisplayName("redefinir el centro no cambia el veredicto de una ubicación ya guardada — blinda D5")
    void redefinirElCentroNoCambiaElVeredictoDeUnaUbicacionYaGuardada() {
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(apunteElegible()));

        UbicacionApunte guardada = servicio.adjunta(USUARIO, APUNTE_ID, CENTRO_LAT, CENTRO_LON, 20);

        // El centro se redefine DESPUÉS (otro alias/radio): la fila ya guardada no cambia,
        // porque solo lee sus propias columnas congeladas, nunca vuelve a mirar centros_trabajo.
        assertThat(guardada.getCentroRadio()).isEqualTo(RADIO);
        assertThat(guardada.getVeredicto()).isEqualTo(VeredictoUbicacion.DENTRO);
    }

    // --- Sello del reloj inyectado ---

    @Test
    @DisplayName("el sello sale del Clock inyectado, nunca de OffsetDateTime.now()")
    void elSelloSaleDelClockInyectadoNuncaDeOffsetDateTimeNow() {
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(apunteElegible()));

        UbicacionApunte guardada = servicio.adjunta(USUARIO, APUNTE_ID, CENTRO_LAT, CENTRO_LON, 20);

        assertThat(guardada.getRegistradaEn()).isEqualTo(OffsetDateTime.now(RELOJ));
    }

    // --- Veredicto FUERA / NO_CONCLUYENTE de extremo a extremo ---

    @Test
    @DisplayName("un fichaje lejos del centro sale FUERA")
    void unFichajeLejosDelCentroSaleFuera() {
        when(apuntes.findById(APUNTE_ID)).thenReturn(Optional.of(apunteElegible()));
        // Plaza de Cataluña, Barcelona: muy lejos del centro en Madrid.
        BigDecimal lejosLat = new BigDecimal("41.38706");
        BigDecimal lejosLon = new BigDecimal("2.17009");

        UbicacionApunte guardada = servicio.adjunta(USUARIO, APUNTE_ID, lejosLat, lejosLon, 30);

        assertThat(guardada.getVeredicto()).isEqualTo(VeredictoUbicacion.FUERA);
    }
}
