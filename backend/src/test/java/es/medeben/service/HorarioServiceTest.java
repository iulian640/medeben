package es.medeben.service;

import es.medeben.domain.horario.Cuadrante;
import es.medeben.domain.horario.DiaCuadrante;
import es.medeben.domain.horario.OrigenHorario;
import es.medeben.domain.horario.Tramo;
import es.medeben.repository.CuadranteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("HorarioService — semana tipo + ediciones por semana, la base de la libreta (D38)")
class HorarioServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    /** "Hoy" fijo para los tests: miércoles 2026-07-08, 12:00 en Madrid. */
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-07-08T10:00:00Z"), ZoneId.of("Europe/Madrid"));

    private CuadranteRepository repositorio;
    private HorarioService servicio;

    @BeforeEach
    void arranque() {
        repositorio = mock(CuadranteRepository.class);
        servicio = new HorarioService(repositorio, RELOJ);
        when(repositorio.save(any(Cuadrante.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static List<DiaCuadrante> semanaValida() {
        DiaCuadrante partido = new DiaCuadrante(List.of(
                new Tramo("12:00", "16:00"), new Tramo("20:00", "00:30")));
        DiaCuadrante seguido = new DiaCuadrante(List.of(new Tramo("09:00", "17:00")));
        DiaCuadrante libre = new DiaCuadrante(List.of());
        return List.of(partido, partido, seguido, seguido, partido, seguido, libre);
    }

    @Test
    @DisplayName("guarda una semana tipo válida (partido de 2 tramos, seguido y libre)")
    void guardaSemanaTipo() {
        Cuadrante guardado = servicio.guardaSemanaTipo(USUARIO, semanaValida());

        assertThat(guardado.getSemanaInicio()).isNull();
        assertThat(guardado.getUsuarioId()).isEqualTo(USUARIO);
        assertThat(guardado.getDias()).hasSize(7);
    }

    @Test
    @DisplayName("el sello de creación sale del reloj inyectado, no del reloj del sistema (review H2: es el dato probatorio)")
    void selloDelRelojInyectado() {
        Cuadrante guardado = servicio.guardaSemanaTipo(USUARIO, semanaValida());
        assertThat(guardado.getCreadoEn()).isEqualTo(OffsetDateTime.now(RELOJ));
    }

    @Test
    @DisplayName("una semana tiene 7 días, ni 6 ni 8")
    void semanaDeSeisDias() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.guardaSemanaTipo(USUARIO, semanaValida().subList(0, 6)));
    }

    @Test
    @DisplayName("máximo 2 tramos por día (D38)")
    void tresTramos() {
        DiaCuadrante triple = new DiaCuadrante(List.of(
                new Tramo("08:00", "10:00"), new Tramo("12:00", "14:00"), new Tramo("16:00", "18:00")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.guardaSemanaTipo(USUARIO, conPrimerDia(triple)));
    }

    @Test
    @DisplayName("hora con formato inválido → error claro")
    void horaInvalida() {
        DiaCuadrante malo = new DiaCuadrante(List.of(new Tramo("25:00", "26:00")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.guardaSemanaTipo(USUARIO, conPrimerDia(malo)));
    }

    @Test
    @DisplayName("tramo vacío (entrada = salida) → error")
    void tramoVacio() {
        DiaCuadrante malo = new DiaCuadrante(List.of(new Tramo("12:00", "12:00")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.guardaSemanaTipo(USUARIO, conPrimerDia(malo)));
    }

    @Test
    @DisplayName("los tramos no pueden solaparse")
    void tramosSolapados() {
        DiaCuadrante malo = new DiaCuadrante(List.of(
                new Tramo("12:00", "16:00"), new Tramo("15:00", "20:00")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.guardaSemanaTipo(USUARIO, conPrimerDia(malo)));
    }

    @Test
    @DisplayName("cruzar medianoche solo puede hacerlo el último tramo del día")
    void medianocheEnPrimerTramo() {
        DiaCuadrante malo = new DiaCuadrante(List.of(
                new Tramo("22:00", "02:00"), new Tramo("03:00", "05:00")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.guardaSemanaTipo(USUARIO, conPrimerDia(malo)));
    }

    @Test
    @DisplayName("el turno de cierre (20:00 → 02:00) es válido: hostelería pura")
    void turnoDeCierre() {
        DiaCuadrante cierre = new DiaCuadrante(List.of(new Tramo("20:00", "02:00")));
        Cuadrante guardado = servicio.guardaSemanaTipo(USUARIO, conPrimerDia(cierre));
        assertThat(guardado.getDias().get(0).tramos()).hasSize(1);
    }

    @Test
    @DisplayName("la edición de una semana concreta se guarda anclada a su lunes")
    void editaSemanaConcreta() {
        LocalDate lunes = LocalDate.of(2026, 7, 6);
        Cuadrante guardado = servicio.guardaSemana(USUARIO, lunes, semanaValida());
        assertThat(guardado.getSemanaInicio()).isEqualTo(lunes);
    }

    @Test
    @DisplayName("la fecha de una semana editada tiene que ser lunes")
    void fechaQueNoEsLunes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.guardaSemana(USUARIO, LocalDate.of(2026, 7, 7), semanaValida()));
    }

    @Test
    @DisplayName("una semana ya sellada (14 días tras su fin) no se puede editar (D38)")
    void semanaSellada() {
        // Semana del 8 de junio: acabó el domingo 14; ventana de confirmación del 15 al 28;
        // sellada desde el 29 de junio a las 00:00. Hoy es 8 de julio → sellada.
        assertThatExceptionOfType(SemanaSelladaException.class)
                .isThrownBy(() -> servicio.guardaSemana(USUARIO, LocalDate.of(2026, 6, 8), semanaValida()));
    }

    @Test
    @DisplayName("la semana pasada aún está dentro de la ventana de 14 días → editable")
    void semanaPasadaDentroDeVentana() {
        Cuadrante guardado = servicio.guardaSemana(USUARIO, LocalDate.of(2026, 6, 29), semanaValida());
        assertThat(guardado.getSemanaInicio()).isEqualTo(LocalDate.of(2026, 6, 29));
    }

    @Test
    @DisplayName("no se puede editar una semana a más de 8 semanas vista (higiene de datos, review M1)")
    void semanaDemasiadoFutura() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.guardaSemana(USUARIO, LocalDate.of(2026, 9, 7), semanaValida()));
    }

    @Test
    @DisplayName("horarioEfectivo prefiere la edición de esa semana sobre la semana tipo")
    void efectivoPrefiereEdicion() {
        LocalDate lunes = LocalDate.of(2026, 7, 6);
        Cuadrante edicion = new Cuadrante(USUARIO, lunes, semanaValida(), OffsetDateTime.now(RELOJ));
        when(repositorio.findTopByUsuarioIdAndSemanaInicioOrderByCreadoEnDescIdDesc(USUARIO, lunes))
                .thenReturn(Optional.of(edicion));

        Optional<HorarioEfectivo> efectivo = servicio.horarioEfectivo(USUARIO, lunes);

        assertThat(efectivo).isPresent();
        assertThat(efectivo.get().origen()).isEqualTo(OrigenHorario.SEMANA_EDITADA);
    }

    @Test
    @DisplayName("sin edición cae a la semana tipo vigente A FINAL de esa semana (una versión posterior no reescribe el pasado)")
    void efectivoUsaSemanaTipoDeLaEpoca() {
        LocalDate lunes = LocalDate.of(2026, 6, 29);
        Cuadrante tipoDeEntonces = new Cuadrante(USUARIO, null, semanaValida(), OffsetDateTime.now(RELOJ));
        when(repositorio.findTopByUsuarioIdAndSemanaInicioOrderByCreadoEnDescIdDesc(USUARIO, lunes))
                .thenReturn(Optional.empty());
        // El servicio debe pedir la última versión creada ANTES del fin de esa semana (lunes+7, 00:00 Madrid)
        OffsetDateTime corte = lunes.plusDays(7).atStartOfDay(ZoneId.of("Europe/Madrid")).toOffsetDateTime();
        when(repositorio.findTopByUsuarioIdAndSemanaInicioIsNullAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(
                eq(USUARIO), eq(corte))).thenReturn(Optional.of(tipoDeEntonces));

        Optional<HorarioEfectivo> efectivo = servicio.horarioEfectivo(USUARIO, lunes);

        assertThat(efectivo).isPresent();
        assertThat(efectivo.get().origen()).isEqualTo(OrigenHorario.SEMANA_TIPO);
        verify(repositorio).findTopByUsuarioIdAndSemanaInicioIsNullAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(
                eq(USUARIO), eq(corte));
    }

    @Test
    @DisplayName("sin semana tipo ni edición → vacío (la app pedirá crear el horario)")
    void efectivoSinNada() {
        when(repositorio.findTopByUsuarioIdAndSemanaInicioOrderByCreadoEnDescIdDesc(eq(USUARIO), any()))
                .thenReturn(Optional.empty());
        when(repositorio.findTopByUsuarioIdAndSemanaInicioIsNullAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(
                eq(USUARIO), any())).thenReturn(Optional.empty());

        assertThat(servicio.horarioEfectivo(USUARIO, LocalDate.of(2026, 7, 6))).isEmpty();
    }

    @Test
    @DisplayName("horariosEfectivosDelRango: la edición gana en su semana, el resto usa la semana tipo (2 consultas en total)")
    void rangoPrefiereEdicionYCaeATipo() {
        LocalDate lunes1 = LocalDate.of(2026, 6, 29);
        LocalDate lunes2 = LocalDate.of(2026, 7, 6);
        Cuadrante edicion = new Cuadrante(USUARIO, lunes2, semanaValida(),
                OffsetDateTime.parse("2026-07-01T10:00:00+02:00"));
        Cuadrante tipo = new Cuadrante(USUARIO, null, semanaValida(),
                OffsetDateTime.parse("2026-01-01T10:00:00+01:00"));
        when(repositorio.findByUsuarioIdAndSemanaInicioBetweenOrderByCreadoEnDescIdDesc(USUARIO, lunes1, lunes2))
                .thenReturn(List.of(edicion));
        when(repositorio.findByUsuarioIdAndSemanaInicioIsNullAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(
                eq(USUARIO), any())).thenReturn(List.of(tipo));

        var rango = servicio.horariosEfectivosDelRango(USUARIO, lunes1, lunes2);

        assertThat(rango).hasSize(2);
        assertThat(rango.get(lunes1)).hasValueSatisfying(
                h -> assertThat(h.origen()).isEqualTo(OrigenHorario.SEMANA_TIPO));
        assertThat(rango.get(lunes2)).hasValueSatisfying(
                h -> assertThat(h.origen()).isEqualTo(OrigenHorario.SEMANA_EDITADA));
        // Exactamente una consulta de ediciones y una de semanas tipo: sin N+1.
        verify(repositorio).findByUsuarioIdAndSemanaInicioBetweenOrderByCreadoEnDescIdDesc(USUARIO, lunes1, lunes2);
        verify(repositorio).findByUsuarioIdAndSemanaInicioIsNullAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(
                eq(USUARIO), any());
    }

    @Test
    @DisplayName("horariosEfectivosDelRango respeta el as-of: una semana tipo creada tras el fin de una semana no aplica a esa semana, sí a las siguientes")
    void rangoRespetaElAsOfPorSemana() {
        LocalDate lunes1 = LocalDate.of(2026, 6, 29); // acaba el 2026-07-06 00:00
        LocalDate lunes2 = LocalDate.of(2026, 7, 6);
        // Creada el martes de la segunda semana: después del fin de la primera.
        Cuadrante tipoNueva = new Cuadrante(USUARIO, null, semanaValida(),
                OffsetDateTime.parse("2026-07-07T09:00:00+02:00"));
        when(repositorio.findByUsuarioIdAndSemanaInicioBetweenOrderByCreadoEnDescIdDesc(USUARIO, lunes1, lunes2))
                .thenReturn(List.of());
        when(repositorio.findByUsuarioIdAndSemanaInicioIsNullAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(
                eq(USUARIO), any())).thenReturn(List.of(tipoNueva));

        var rango = servicio.horariosEfectivosDelRango(USUARIO, lunes1, lunes2);

        assertThat(rango.get(lunes1)).isEmpty(); // el pasado no se reescribe (D38)
        assertThat(rango.get(lunes2)).hasValueSatisfying(
                h -> assertThat(h.origen()).isEqualTo(OrigenHorario.SEMANA_TIPO));
    }

    @Test
    @DisplayName("horariosEfectivosDelRango con varias versiones: cada semana usa la ÚLTIMA versión que ya existía a su fin")
    void rangoEligeLaVersionVigenteDeCadaSemana() {
        LocalDate lunes1 = LocalDate.of(2026, 6, 29);
        LocalDate lunes2 = LocalDate.of(2026, 7, 6);
        Cuadrante tipoVieja = new Cuadrante(USUARIO, null, semanaValida(),
                OffsetDateTime.parse("2026-01-01T10:00:00+01:00"));
        Cuadrante tipoNueva = new Cuadrante(USUARIO, null, semanaValida(),
                OffsetDateTime.parse("2026-07-07T09:00:00+02:00"));
        when(repositorio.findByUsuarioIdAndSemanaInicioBetweenOrderByCreadoEnDescIdDesc(USUARIO, lunes1, lunes2))
                .thenReturn(List.of());
        // El finder devuelve más reciente primero, como el ORDER BY del repositorio.
        when(repositorio.findByUsuarioIdAndSemanaInicioIsNullAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(
                eq(USUARIO), any())).thenReturn(List.of(tipoNueva, tipoVieja));

        var rango = servicio.horariosEfectivosDelRango(USUARIO, lunes1, lunes2);

        assertThat(rango.get(lunes1)).hasValueSatisfying(
                h -> assertThat(h.definidoEn()).isEqualTo(tipoVieja.getCreadoEn()));
        assertThat(rango.get(lunes2)).hasValueSatisfying(
                h -> assertThat(h.definidoEn()).isEqualTo(tipoNueva.getCreadoEn()));
    }

    @Test
    @DisplayName("horariosEfectivosDelRango exige lunes y un rango bien ordenado")
    void rangoValidaLosLimites() {
        assertThatIllegalArgumentException().isThrownBy(() -> servicio.horariosEfectivosDelRango(
                USUARIO, LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 13)));
        assertThatIllegalArgumentException().isThrownBy(() -> servicio.horariosEfectivosDelRango(
                USUARIO, LocalDate.of(2026, 7, 6), LocalDate.of(2026, 6, 29)));
    }

    @Test
    @DisplayName("guardar nunca borra: el repositorio solo recibe saves (libreta append-only)")
    void soloSeAnade() {
        servicio.guardaSemanaTipo(USUARIO, semanaValida());
        ArgumentCaptor<Cuadrante> captor = ArgumentCaptor.forClass(Cuadrante.class);
        verify(repositorio).save(captor.capture());
        assertThat(captor.getValue().getId()).isNotNull();
    }

    private static List<DiaCuadrante> conPrimerDia(DiaCuadrante dia) {
        List<DiaCuadrante> semana = new java.util.ArrayList<>(semanaValida());
        semana.set(0, dia);
        return List.copyOf(semana);
    }
}
