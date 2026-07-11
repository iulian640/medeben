package es.medeben.service;

import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.EstadoDia;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;
import es.medeben.repository.ApunteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FichajeService — el diario de la libreta sellada (D38): apuntes append-only con origen")
class FichajeServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** "Ahora" fijo: miércoles 2026-07-08, 12:00 en Madrid. */
    private static final Clock RELOJ = relojA("2026-07-08T12:00");
    private static final LocalDate HOY = LocalDate.of(2026, 7, 8);

    private ApunteRepository repositorio;
    private FichajeService servicio;

    private static Clock relojA(String fechaHoraMadrid) {
        Instant instante = java.time.LocalDateTime.parse(fechaHoraMadrid).atZone(MADRID).toInstant();
        return Clock.fixed(instante, MADRID);
    }

    @BeforeEach
    void arranque() {
        repositorio = mock(ApunteRepository.class);
        servicio = new FichajeService(repositorio, RELOJ);
        when(repositorio.save(any(Apunte.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- fichar: origen según cuándo se apunta ---

    @Test
    @DisplayName("fichar el día de hoy → origen CONFIRMADO con sello del reloj inyectado")
    void ficharHoyEsConfirmado() {
        Apunte apunte = servicio.apunta(USUARIO, HOY, TipoApunte.SALIDA, "23:45", null, false);

        assertThat(apunte.getOrigen()).isEqualTo(OrigenApunte.CONFIRMADO);
        assertThat(apunte.getRegistradoEn()).isEqualTo(OffsetDateTime.now(RELOJ));
        assertThat(apunte.getHora()).isEqualTo("23:45");
    }

    @Test
    @DisplayName("turno de cierre: la salida de AYER fichada esta madrugada (antes del mediodía) sigue siendo CONFIRMADO")
    void turnoDeCierreEsConfirmado() {
        FichajeService deMadrugada = new FichajeService(repositorio, relojA("2026-07-08T02:15"));

        Apunte apunte = deMadrugada.apunta(USUARIO, HOY.minusDays(1), TipoApunte.SALIDA, "02:00", null, false);

        assertThat(apunte.getOrigen()).isEqualTo(OrigenApunte.CONFIRMADO);
    }

    @Test
    @DisplayName("la salida de ayer apuntada hoy a mediodía ya NO es al momento → RECONSTRUIDO")
    void ayerAMediodiaEsReconstruido() {
        Apunte apunte = servicio.apunta(USUARIO, HOY.minusDays(1), TipoApunte.SALIDA, "23:00", null, false);

        assertThat(apunte.getOrigen()).isEqualTo(OrigenApunte.RECONSTRUIDO);
    }

    @Test
    @DisplayName("reconstruir un día de la semana pasada (dentro de la ventana de 14 días) → RECONSTRUIDO")
    void dentroDeVentanaEsReconstruido() {
        Apunte apunte = servicio.apunta(USUARIO, HOY.minusDays(7), TipoApunte.ENTRADA, "12:00", null, false);

        assertThat(apunte.getOrigen()).isEqualTo(OrigenApunte.RECONSTRUIDO);
    }

    // --- sellado ---

    @Test
    @DisplayName("un día sellado (14 días tras acabar) no se ficha sin confirmación explícita → DiaSelladoException")
    void diaSelladoRechaza() {
        assertThatExceptionOfType(DiaSelladoException.class)
                .isThrownBy(() -> servicio.apunta(USUARIO, HOY.minusDays(20), TipoApunte.SALIDA, "23:00", null, false));
    }

    @Test
    @DisplayName("rectificación tardía confirmada sobre día sellado → se guarda con origen RECTIFICACION_TARDIA")
    void rectificacionTardiaConfirmada() {
        Apunte apunte = servicio.apunta(USUARIO, HOY.minusDays(20), TipoApunte.SALIDA, "23:00", null, true);

        assertThat(apunte.getOrigen()).isEqualTo(OrigenApunte.RECTIFICACION_TARDIA);
    }

    @Test
    @DisplayName("el día se sella exactamente 14 días después de acabar: día 23 de junio, sellado desde el 8 de julio 00:00")
    void bordeDelSellado() {
        // 23 jun acabó el 24 a las 00:00; +14 días = 8 jul 00:00. Hoy 8 jul 12:00 → sellado.
        assertThatExceptionOfType(DiaSelladoException.class)
                .isThrownBy(() -> servicio.apunta(USUARIO, LocalDate.of(2026, 6, 23), TipoApunte.SALIDA, "23:00", null, false));
        // El 24 de junio se sella el 9 de julio → hoy aún editable.
        Apunte apunte = servicio.apunta(USUARIO, LocalDate.of(2026, 6, 24), TipoApunte.SALIDA, "23:00", null, false);
        assertThat(apunte.getOrigen()).isEqualTo(OrigenApunte.RECONSTRUIDO);
    }

    // --- validación ---

    @Test
    @DisplayName("no se puede fichar el futuro")
    void fechaFutura() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.apunta(USUARIO, HOY.plusDays(1), TipoApunte.ENTRADA, "09:00", null, false));
    }

    @Test
    @DisplayName("entrada/salida exigen hora válida HH:mm")
    void horaInvalida() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.apunta(USUARIO, HOY, TipoApunte.ENTRADA, "9h", null, false));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.apunta(USUARIO, HOY, TipoApunte.ENTRADA, null, null, false));
    }

    @Test
    @DisplayName("la ausencia va sin hora y puede llevar motivo; el motivo tiene tope de longitud")
    void ausencia() {
        Apunte apunte = servicio.apunta(USUARIO, HOY, TipoApunte.AUSENCIA, null, "enfermo", false);
        assertThat(apunte.getMotivo()).isEqualTo("enfermo");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.apunta(USUARIO, HOY, TipoApunte.AUSENCIA, "09:00", null, false));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.apunta(USUARIO, HOY, TipoApunte.AUSENCIA, null, "x".repeat(201), false));
    }

    @Test
    @DisplayName("el motivo solo existe en las ausencias: minimización RGPD, un fichaje no lleva texto libre (review)")
    void motivoSoloEnAusencias() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.apunta(USUARIO, HOY, TipoApunte.SALIDA, "23:00", "me encontraba mal", false));
    }

    @Test
    @DisplayName("no se aceptan fechas de hace más de 2 años (higiene de datos, review)")
    void fechaDemasiadoAntigua() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.apunta(USUARIO, HOY.minusYears(2).minusDays(1),
                        TipoApunte.SALIDA, "23:00", null, true));
    }

    // --- estado del día derivado del diario ---

    @Test
    @DisplayName("sin apuntes y sin sellar → PENDIENTE")
    void diaPendiente() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of());

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.PENDIENTE);
        assertThat(estado.sellado()).isFalse();
    }

    @Test
    @DisplayName("sin apuntes y ya sellado → HUECO para siempre (un diario real tiene huecos)")
    void diaHueco() {
        LocalDate viejo = HOY.minusDays(30);
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, viejo))
                .thenReturn(List.of());

        EstadoDia estado = servicio.estadoDia(USUARIO, viejo);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.HUECO);
        assertThat(estado.sellado()).isTrue();
    }

    @Test
    @DisplayName("entrada + salida → COMPLETO, con las horas trabajadas (turno de cierre incluido: 20:00→02:00 = 6h)")
    void diaCompleto() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.SALIDA, "02:00", "2026-07-09T02:03")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.minutosTrabajados()).isEqualTo(6 * 60);
    }

    @Test
    @DisplayName("solo entrada → EN_CURSO")
    void diaEnCurso() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(apunte(TipoApunte.ENTRADA, "12:00", "2026-07-08T12:01")));

        assertThat(servicio.estadoDia(USUARIO, HOY).estado()).isEqualTo(EstadoDia.Estado.EN_CURSO);
    }

    @Test
    @DisplayName("una corrección no borra nada: gana el último apunte de cada tipo, pero el diario los enseña todos")
    void correccionGanaLaUltima() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "12:00", "2026-07-08T12:01"),
                        apunte(TipoApunte.SALIDA, "20:00", "2026-07-08T20:02"),
                        apunte(TipoApunte.SALIDA, "21:30", "2026-07-08T21:31")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.minutosTrabajados()).isEqualTo((int) java.time.Duration.ofHours(9).plusMinutes(30).toMinutes());
        assertThat(estado.apuntes()).hasSize(3);
    }

    @Test
    @DisplayName("los tramos derivados viajan en el estado: la UI enseña la lectura, no la pila de apuntes")
    void tramosDerivadosExpuestos() {
        // La ráfaga real del QA: entrada, salida, y una lluvia de toques a las
        // 14:02 que se corrigen unos a otros. La lectura queda en dos tramos.
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "10:00", "2026-07-08T10:01"),
                        apunte(TipoApunte.SALIDA, "20:00", "2026-07-08T11:00"),
                        apunte(TipoApunte.SALIDA, "14:02", "2026-07-08T14:02"),
                        apunte(TipoApunte.ENTRADA, "14:02", "2026-07-08T14:02"),
                        apunte(TipoApunte.SALIDA, "14:02", "2026-07-08T14:02"),
                        apunte(TipoApunte.ENTRADA, "14:02", "2026-07-08T14:02"),
                        apunte(TipoApunte.SALIDA, "14:02", "2026-07-08T14:02")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.tramos()).containsExactly(
                new EstadoDia.TramoDia("10:00", "14:02"),
                new EstadoDia.TramoDia("14:02", "14:02"));
        assertThat(estado.entradaAbierta()).isNull();
        assertThat(estado.minutosTrabajados()).isEqualTo(4 * 60 + 2);
    }

    @Test
    @DisplayName("con la jornada abierta, la entrada sin salida viaja como entradaAbierta")
    void entradaAbiertaExpuesta() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(apunte(TipoApunte.ENTRADA, "12:00", "2026-07-08T12:01")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.tramos()).isEmpty();
        assertThat(estado.entradaAbierta()).isEqualTo("12:00");
    }

    @Test
    @DisplayName("una AUSENCIA invalida los fichajes anteriores: corregir después 'sí entré' NO resucita la salida vieja (review HIGH)")
    void ausenciaEsFrontera() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "09:00", "2026-07-08T09:01"),
                        apunte(TipoApunte.SALIDA, "17:00", "2026-07-08T17:02"),
                        apunte(TipoApunte.AUSENCIA, null, "2026-07-08T18:00"),
                        apunte(TipoApunte.ENTRADA, "08:00", "2026-07-08T19:00")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.EN_CURSO);
        assertThat(estado.minutosTrabajados()).isEqualTo(-1);
    }

    @Test
    @DisplayName("AUSENCIA después de un día completo → el día queda AUSENCIA (la corrección gana)")
    void ausenciaTrasDiaCompleto() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "09:00", "2026-07-08T09:01"),
                        apunte(TipoApunte.SALIDA, "17:00", "2026-07-08T17:02"),
                        apunte(TipoApunte.AUSENCIA, null, "2026-07-08T18:00")));

        assertThat(servicio.estadoDia(USUARIO, HOY).estado()).isEqualTo(EstadoDia.Estado.AUSENCIA);
    }

    // --- turno partido (D38: hasta 2 tramos declarados) ---

    @Test
    @DisplayName("turno partido completo → COMPLETO con la SUMA de los dos tramos, no solo el último")
    void turnoPartidoCompleto() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "12:00", "2026-07-08T12:01"),
                        apunte(TipoApunte.SALIDA, "16:00", "2026-07-08T16:02"),
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.SALIDA, "23:00", "2026-07-08T23:02")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.minutosTrabajados()).isEqualTo(7 * 60);
    }

    @Test
    @DisplayName("turno partido a mitad del segundo tramo → EN_CURSO con los minutos del primer tramo (no 20h fantasma)")
    void turnoPartidoEnCurso() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "12:00", "2026-07-08T12:01"),
                        apunte(TipoApunte.SALIDA, "16:00", "2026-07-08T16:02"),
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T20:01")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.EN_CURSO);
        assertThat(estado.minutosTrabajados()).isEqualTo(4 * 60);
    }

    @Test
    @DisplayName("corrección de salida tras turno partido completo: la última salida corrige el segundo tramo, no abre otro")
    void correccionDeSalidaEnTurnoPartido() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "12:00", "2026-07-08T12:01"),
                        apunte(TipoApunte.SALIDA, "16:00", "2026-07-08T16:02"),
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.SALIDA, "23:00", "2026-07-08T23:02"),
                        apunte(TipoApunte.SALIDA, "23:30", "2026-07-08T23:35")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        // Primer tramo intacto (240) + segundo tramo corregido a 20:00→23:30 (210).
        assertThat(estado.minutosTrabajados()).isEqualTo(240 + 210);
        assertThat(estado.apuntes()).hasSize(5);
    }

    @Test
    @DisplayName("turno partido con cierre de madrugada: el cruce de medianoche se calcula por tramo")
    void turnoPartidoConCruceDeMedianoche() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "12:00", "2026-07-08T12:01"),
                        apunte(TipoApunte.SALIDA, "16:00", "2026-07-08T16:02"),
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.SALIDA, "01:00", "2026-07-09T01:03")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.minutosTrabajados()).isEqualTo(240 + 300);
    }

    @Test
    @DisplayName("la AUSENCIA sigue siendo frontera con turno partido: invalida el tramo anterior, cuenta solo lo posterior")
    void ausenciaFronteraConTurnoPartido() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "09:00", "2026-07-08T09:01"),
                        apunte(TipoApunte.SALIDA, "13:00", "2026-07-08T13:02"),
                        apunte(TipoApunte.AUSENCIA, null, "2026-07-08T14:00"),
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.SALIDA, "23:00", "2026-07-08T23:02")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.minutosTrabajados()).isEqualTo(3 * 60);
    }

    @Test
    @DisplayName("corrección de entrada con el día ya completo: corrige el último tramo cerrado, NO reabre el día (review HIGH)")
    void correccionDeEntradaConDiaCompletoNoReabre() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "12:00", "2026-07-08T12:01"),
                        apunte(TipoApunte.SALIDA, "16:00", "2026-07-08T16:02"),
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.SALIDA, "23:00", "2026-07-08T23:02"),
                        apunte(TipoApunte.ENTRADA, "19:45", "2026-07-08T23:40")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        // Cupo D38 (2 tramos) cubierto: la última entrada no abre un tercer
        // tramo fantasma, corrige la entrada del segundo (19:45→23:00 = 195).
        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.minutosTrabajados()).isEqualTo(240 + 195);
        assertThat(estado.apuntes()).hasSize(5);
    }

    @Test
    @DisplayName("con un tramo abierto, una nueva entrada corrige ESA entrada (gana la última) sin tocar tramos cerrados")
    void correccionDeEntradaConTramoAbierto() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "12:00", "2026-07-08T12:01"),
                        apunte(TipoApunte.SALIDA, "16:00", "2026-07-08T16:02"),
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.ENTRADA, "20:15", "2026-07-08T20:20"),
                        apunte(TipoApunte.SALIDA, "23:00", "2026-07-08T23:02")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        // Primer tramo intacto (240) + segundo con la entrada corregida
        // 20:15→23:00 (165). Limitación documentada: una corrección pensada
        // para el primer tramo también caería aquí (no hay id de tramo).
        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.minutosTrabajados()).isEqualTo(240 + 165);
    }

    @Test
    @DisplayName("salida mal dirigida que cierra el tramo abierto con >16h: el total queda sin calcular, no infla el día (review HIGH)")
    void correccionDeSalidaMalDirigidaConTramoAbierto() {
        // El usuario quería corregir la salida del PRIMER tramo (16:00→16:30),
        // pero como el segundo está abierto, su apunte lo cierra: 20:00→16:30
        // "cruza la medianoche" (1230 min). El techo de cordura lo detecta.
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "12:00", "2026-07-08T12:01"),
                        apunte(TipoApunte.SALIDA, "16:00", "2026-07-08T16:02"),
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.SALIDA, "16:30", "2026-07-08T20:30")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        // Limitación documentada: el EN_CURSO real se pierde (no hay id de
        // tramo), pero el día NO se reporta con ~24,5h trabajadas.
        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.minutosTrabajados()).isEqualTo(-1);
    }

    @Test
    @DisplayName("salida mal dirigida con los dos tramos ya cerrados: el techo de cordura evita las ~24h fantasma (review HIGH)")
    void correccionDeSalidaMalDirigidaConTramosCerrados() {
        // Quería decir "el primer tramo acabó a las 16:30", pero la corrección
        // cae por posición en el último tramo cerrado: 20:00→16:30 = 1230 min.
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "12:00", "2026-07-08T12:01"),
                        apunte(TipoApunte.SALIDA, "16:00", "2026-07-08T16:02"),
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.SALIDA, "23:00", "2026-07-08T23:02"),
                        apunte(TipoApunte.SALIDA, "16:30", "2026-07-08T23:40")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.minutosTrabajados()).isEqualTo(-1);
    }

    @Test
    @DisplayName("tramo con entrada == salida (doble toque) cuenta 0 minutos, no 24h (review MEDIUM)")
    void entradaIgualQueSalidaCuentaCero() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.ENTRADA, "12:00", "2026-07-08T12:01"),
                        apunte(TipoApunte.SALIDA, "16:00", "2026-07-08T16:02"),
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.SALIDA, "20:00", "2026-07-08T20:02")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        // El tramo degenerado suma 0; el primero se conserva (240).
        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.minutosTrabajados()).isEqualTo(240);
    }

    @Test
    @DisplayName("salida sin entrada (huérfana sola, nunca emparejada) → el día sigue PENDIENTE (comportamiento intacto)")
    void salidaSinEntrada() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(apunte(TipoApunte.SALIDA, "17:00", "2026-07-08T17:02")));

        assertThat(servicio.estadoDia(USUARIO, HOY).estado()).isEqualTo(EstadoDia.Estado.PENDIENTE);
    }

    // --- salida huérfana: reconstruir apuntando primero la salida (issue #221) ---

    @Test
    @DisplayName("issue #221: reconstruir un día apuntando la SALIDA (20:00) antes que la ENTRADA (10:00) → COMPLETO 10h, no 'En curso'")
    void salidaHuerfanaAntesQueEntradaFormaTramo() {
        // El caso literal del QA: "me acuerdo de a qué hora salí" primero. Antes
        // dejaba el día EN_CURSO y esas horas desaparecían del resumen del mes.
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.SALIDA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.ENTRADA, "10:00", "2026-07-08T20:05")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.minutosTrabajados()).isEqualTo(10 * 60);
        assertThat(estado.tramos()).containsExactly(new EstadoDia.TramoDia("10:00", "20:00"));
        assertThat(estado.entradaAbierta()).isNull();
    }

    @Test
    @DisplayName("dos SALIDAS huérfanas distintas antes de la entrada: ambiguo (¿corrección de la misma o dos salidas de un partido?) → no se auto-empareja, queda EN_CURSO, sin horas fabricadas")
    void dosSalidasHuerfanasNoSeAutoEmparejan() {
        // Con dos salidas sueltas no hay forma de saber si la segunda corrige a la
        // primera (misma salida) o si son las salidas de dos tramos de un turno
        // partido. Emparejar la "última" con la primera entrada fabricaba un tramo
        // que no fichó nadie (ver issue #221 / regresión de abajo). Ante la duda no
        // se auto-completa: la entrada abre tramo y el día queda EN_CURSO, igual que
        // antes de la feature; el usuario lo cierra.
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.SALIDA, "19:00", "2026-07-08T20:00"),
                        apunte(TipoApunte.SALIDA, "20:00", "2026-07-08T20:02"),
                        apunte(TipoApunte.ENTRADA, "10:00", "2026-07-08T20:05")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.EN_CURSO);
        assertThat(estado.entradaAbierta()).isEqualTo("10:00");
        assertThat(estado.tramos()).isEmpty();
    }

    @Test
    @DisplayName("la AUSENCIA es frontera: descarta también la huérfana pendiente, la entrada posterior abre tramo nuevo → EN_CURSO")
    void ausenciaDescartaLaHuerfanaPendiente() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.SALIDA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.AUSENCIA, null, "2026-07-08T20:03"),
                        apunte(TipoApunte.ENTRADA, "10:00", "2026-07-08T20:05")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.EN_CURSO);
        assertThat(estado.entradaAbierta()).isEqualTo("10:00");
        assertThat(estado.tramos()).isEmpty();
    }

    @Test
    @DisplayName("issue #221 regresión: SALIDA huérfana (10:00) antes que la ENTRADA de un turno abierto (20:00) NO fabrica un tramo nocturno de 14h — queda EN_CURSO, no COMPLETO")
    void salidaHuerfanaAntesDeTurnoAbiertoNoFabricaHoras() {
        // Misclick/resto de reconstrucción: SALIDA 10:00 como primer apunte del día
        // y luego el turno real de tarde (ENTRADA 20:00) todavía sin cerrar.
        // Emparejarlas daría el tramo (20:00, 10:00) que minutosEntre trata como
        // cruce de medianoche = 14h nocturnas fantasma (bajo el techo de 16h, así
        // que ResumenMensualService las contaría como horas debidas). El emparejado
        // de huérfana solo vale si forma un tramo del mismo día (entrada < salida):
        // aquí la entrada es POSTERIOR a la salida, así que no se empareja.
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.SALIDA, "10:00", "2026-07-08T10:02"),
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T20:05")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.EN_CURSO);
        assertThat(estado.entradaAbierta()).isEqualTo("20:00");
        assertThat(estado.tramos()).isEmpty();
        assertThat(estado.minutosTrabajados()).isEqualTo(-1);
    }

    @Test
    @DisplayName("huérfana que cruzaría medianoche (SALIDA 02:00 antes que ENTRADA 20:00): indistinguible de una salida espuria + turno abierto → no se empareja, queda EN_CURSO")
    void huerfanaQueCruzariaMedianocheNoSeEmpareja() {
        // Un turno de cierre real (20:00 → 02:00) reconstruido "salida primero" tiene
        // exactamente la misma forma que una salida espuria 02:00 seguida de un turno
        // de tarde aún abierto: entrada POSTERIOR a la salida. No se puede distinguir,
        // así que no se auto-completa (evita fabricar horas, ver regresión de arriba).
        // El cierre nocturno legítimo se reconstruye "entrada primero" (ENTRADA 20:00,
        // SALIDA 02:00), que sí cierra el tramo por la vía normal.
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.SALIDA, "02:00", "2026-07-08T02:03"),
                        apunte(TipoApunte.ENTRADA, "20:00", "2026-07-08T08:00")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.EN_CURSO);
        assertThat(estado.entradaAbierta()).isEqualTo("20:00");
        assertThat(estado.tramos()).isEmpty();
    }

    @Test
    @DisplayName("issue #221 regresión: dos SALIDAS huérfanas distintas + dos ENTRADAS (turno partido reconstruido salidas-primero) NO fabrican un tramo mezclado — queda EN_CURSO")
    void dosHuerfanasDistintasConDosEntradasNoFabricanTramo() {
        // El usuario reconstruye un turno partido apuntando primero las dos salidas
        // (12:00, 22:00) y luego las dos entradas (08:00, 14:00). Con una sola ranura
        // de huérfana "gana la última" (22:00) y se emparejaba con la primera entrada
        // (08:00) → tramo (08:00, 22:00) = 14h que mezcla la entrada de la mañana con
        // la salida de la tarde, ninguna hora que se fichó. Al ser ambiguo (dos
        // salidas sueltas) no se auto-empareja: el día queda EN_CURSO desde 14:00.
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.SALIDA, "12:00", "2026-07-08T20:00"),
                        apunte(TipoApunte.SALIDA, "22:00", "2026-07-08T20:02"),
                        apunte(TipoApunte.ENTRADA, "08:00", "2026-07-08T20:05"),
                        apunte(TipoApunte.ENTRADA, "14:00", "2026-07-08T20:08")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.EN_CURSO);
        assertThat(estado.entradaAbierta()).isEqualTo("14:00");
        assertThat(estado.tramos()).isEmpty();
    }

    @Test
    @DisplayName("issue #221 regresión: si el turno partido ambiguo se cierra luego con una SALIDA, solo cuenta el tramo real, sin solape de 23h")
    void turnoPartidoAmbiguoAlCompletarNoSobrecuenta() {
        // Continuación del caso anterior: llega la SALIDA 23:00 que cierra la entrada
        // abierta (14:00). Como no se fabricó el tramo (08:00, 22:00), no hay solape
        // 14:00-22:00 contado dos veces: el día cuenta solo (14:00, 23:00) = 9h.
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.SALIDA, "12:00", "2026-07-08T20:00"),
                        apunte(TipoApunte.SALIDA, "22:00", "2026-07-08T20:02"),
                        apunte(TipoApunte.ENTRADA, "08:00", "2026-07-08T20:05"),
                        apunte(TipoApunte.ENTRADA, "14:00", "2026-07-08T20:08"),
                        apunte(TipoApunte.SALIDA, "23:00", "2026-07-08T23:05")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.tramos()).containsExactly(new EstadoDia.TramoDia("14:00", "23:00"));
        assertThat(estado.minutosTrabajados()).isEqualTo(9 * 60);
    }

    @Test
    @DisplayName("tras emparejar la huérfana, una SALIDA posterior corrige la salida de ese tramo (regla existente sigue viva)")
    void salidaPosteriorCorrigeElTramoDeLaHuerfana() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.SALIDA, "20:00", "2026-07-08T20:01"),
                        apunte(TipoApunte.ENTRADA, "10:00", "2026-07-08T20:05"),
                        apunte(TipoApunte.SALIDA, "21:00", "2026-07-08T21:02")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.tramos()).containsExactly(new EstadoDia.TramoDia("10:00", "21:00"));
        assertThat(estado.minutosTrabajados()).isEqualTo(11 * 60);
    }

    @Test
    @DisplayName("emparejar la huérfana no salta el cupo D38: con dos tramos ya llenos, la entrada corrige el último, no fabrica un tercero")
    void huerfanaEmparejadaRespetaElCupoDeTramos() {
        // La huérfana (19:00) la empareja la primera entrada (10:00) en el tramo 1.
        // Luego se declara el tramo 2 y una entrada más cae por la regla de cupo
        // lleno sobre el tramo 2 (gana la última), sin resucitar la huérfana ya
        // consumida ni abrir un tercer tramo.
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.SALIDA, "19:00", "2026-07-08T20:00"),
                        apunte(TipoApunte.ENTRADA, "10:00", "2026-07-08T20:05"),
                        apunte(TipoApunte.ENTRADA, "08:00", "2026-07-08T20:10"),
                        apunte(TipoApunte.SALIDA, "12:00", "2026-07-08T20:12"),
                        apunte(TipoApunte.ENTRADA, "09:00", "2026-07-08T20:20")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.tramos()).containsExactly(
                new EstadoDia.TramoDia("10:00", "19:00"),
                new EstadoDia.TramoDia("09:00", "12:00"));
        assertThat(estado.tramos()).hasSize(2); // el cupo D38 se respeta: nunca un tercer tramo
    }

    @Test
    @DisplayName("huérfana emparejada que inflaría la jornada (>16h): el techo de cordura deja el total sin calcular, el resumen no la sobrecuenta")
    void huerfanaEmparejadaQueInflaLaJornadaSeQuedaSinTotal() {
        // Salida 23:00 apuntada antes que la entrada 06:00 → tramo 06:00-23:00 =
        // 17h, por encima del techo de cordura. minutosTrabajados = -1 y así
        // ResumenMensualService.minutosReales lo excluye (no sobrecuenta dinero).
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(
                        apunte(TipoApunte.SALIDA, "23:00", "2026-07-08T23:03"),
                        apunte(TipoApunte.ENTRADA, "06:00", "2026-07-08T23:10")));

        EstadoDia estado = servicio.estadoDia(USUARIO, HOY);

        assertThat(estado.estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estado.minutosTrabajados()).isEqualTo(-1);
    }

    @Test
    @DisplayName("si el último apunte es AUSENCIA, el día queda AUSENCIA (registrada, con motivo)")
    void diaAusencia() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(apunte(TipoApunte.AUSENCIA, null, "2026-07-08T12:01")));

        assertThat(servicio.estadoDia(USUARIO, HOY).estado()).isEqualTo(EstadoDia.Estado.AUSENCIA);
    }

    @Test
    @DisplayName("estadoDia informa desde cuándo se sella el día (para el contador de la UI)")
    void informaSelladoDesde() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of());

        assertThat(servicio.estadoDia(USUARIO, HOY).selladoDesde()).isEqualTo(HOY.plusDays(15));
    }

    // --- estado de un periodo entero: una consulta por rango (sin N+1) ---

    @Test
    @DisplayName("estadosDelPeriodo: UNA sola consulta por rango, deriva cada día en memoria (sin N+1, review HIGH)")
    void estadosDelPeriodoUnaSolaConsulta() {
        LocalDate lunes = LocalDate.of(2026, 7, 6);
        LocalDate martes = LocalDate.of(2026, 7, 7);
        LocalDate miercoles = HOY; // 2026-07-08
        when(repositorio.findByUsuarioIdAndFechaBetweenOrderByFechaAscRegistradoEnAscIdAsc(
                USUARIO, lunes, miercoles))
                .thenReturn(List.of(
                        apunteEn(lunes, TipoApunte.ENTRADA, "09:00", "2026-07-06T09:01"),
                        apunteEn(lunes, TipoApunte.SALIDA, "17:00", "2026-07-06T17:02"),
                        apunteEn(martes, TipoApunte.ENTRADA, "10:00", "2026-07-07T10:01")));

        Map<LocalDate, EstadoDia> estados = servicio.estadosDelPeriodo(USUARIO, lunes, miercoles);

        assertThat(estados).containsOnlyKeys(lunes, martes, miercoles);
        assertThat(estados.get(lunes).estado()).isEqualTo(EstadoDia.Estado.COMPLETO);
        assertThat(estados.get(lunes).minutosTrabajados()).isEqualTo(8 * 60);
        assertThat(estados.get(martes).estado()).isEqualTo(EstadoDia.Estado.EN_CURSO); // solo entrada
        assertThat(estados.get(miercoles).estado()).isEqualTo(EstadoDia.Estado.PENDIENTE); // sin apuntes, reciente
        // El N+1 queda descartado: una única consulta de rango, ninguna por día.
        verify(repositorio, times(1)).findByUsuarioIdAndFechaBetweenOrderByFechaAscRegistradoEnAscIdAsc(
                USUARIO, lunes, miercoles);
        verify(repositorio, never()).findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(any(), any());
    }

    private static Apunte apunte(TipoApunte tipo, String hora, String registradoMadrid) {
        return apunteEn(HOY, tipo, hora, registradoMadrid);
    }

    private static Apunte apunteEn(LocalDate fecha, TipoApunte tipo, String hora, String registradoMadrid) {
        OffsetDateTime sello = java.time.LocalDateTime.parse(registradoMadrid).atZone(MADRID).toOffsetDateTime();
        return new Apunte(USUARIO, fecha, tipo, hora,
                tipo == TipoApunte.AUSENCIA ? "motivo" : null,
                tipo == TipoApunte.AUSENCIA || hora == null ? OrigenApunte.RECONSTRUIDO : OrigenApunte.CONFIRMADO,
                sello);
    }
}
