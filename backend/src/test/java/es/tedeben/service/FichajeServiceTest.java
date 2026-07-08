package es.tedeben.service;

import es.tedeben.domain.fichaje.Apunte;
import es.tedeben.domain.fichaje.EstadoDia;
import es.tedeben.domain.fichaje.OrigenApunte;
import es.tedeben.domain.fichaje.TipoApunte;
import es.tedeben.repository.ApunteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
    @DisplayName("salida sin entrada → el día sigue PENDIENTE de completar")
    void salidaSinEntrada() {
        when(repositorio.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(USUARIO, HOY))
                .thenReturn(List.of(apunte(TipoApunte.SALIDA, "17:00", "2026-07-08T17:02")));

        assertThat(servicio.estadoDia(USUARIO, HOY).estado()).isEqualTo(EstadoDia.Estado.PENDIENTE);
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

    private static Apunte apunte(TipoApunte tipo, String hora, String registradoMadrid) {
        OffsetDateTime sello = java.time.LocalDateTime.parse(registradoMadrid).atZone(MADRID).toOffsetDateTime();
        return new Apunte(USUARIO, HOY, tipo, hora,
                tipo == TipoApunte.AUSENCIA ? "motivo" : null,
                tipo == TipoApunte.AUSENCIA || hora == null ? OrigenApunte.RECONSTRUIDO : OrigenApunte.CONFIRMADO,
                sello);
    }
}
