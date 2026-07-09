package es.medeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.medeben.controller.ResumenIncompletoException;
import es.medeben.domain.fichaje.EstadoDia;
import es.medeben.domain.horario.DiaCuadrante;
import es.medeben.domain.horario.OrigenHorario;
import es.medeben.domain.horario.Tramo;
import es.medeben.domain.usuario.Perfil;
import es.medeben.repository.ConvenioCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ResumenMensualService — 'te deben X€ este mes' (D12): teórico vs real, extra sin compensar déficit")
class ResumenMensualServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** "Hoy" fijo en agosto: julio (mes bajo prueba) queda entero en el pasado. */
    private static final Clock RELOJ = relojA("2026-08-15T12:00");
    private static final YearMonth JULIO = YearMonth.of(2026, 7);

    private static final BigDecimal MINIMO_CONVENIO = new BigDecimal("1250.91");
    private static final BigDecimal PRECIO_HORA = new BigDecimal("10.90");
    private static final String ET_URL = Cita.URL_ESTATUTO_TRABAJADORES;

    /** Semana tipo de 8 h/día; el lunes es partido (2 tramos) para ejercitar la suma de tramos. */
    private static final HorarioEfectivo SEMANA_8H = new HorarioEfectivo(List.of(
            new DiaCuadrante(List.of(new Tramo("09:00", "13:00"), new Tramo("17:00", "21:00"))), // lunes partido
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00"))),
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00"))),
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00"))),
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00"))),
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00"))),
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00")))
    ), OrigenHorario.SEMANA_TIPO, OffsetDateTime.parse("2026-01-01T00:00:00+01:00"));

    private PerfilService perfiles;
    private HorarioService horarios;
    private FichajeService fichajes;
    private TablaSalarialService tablas;
    private CalculoConvenioService calculo;
    private ResumenMensualService servicio;

    /** Diario por fecha; lo no configurado usa el estado por defecto (HUECO). */
    private final Map<LocalDate, EstadoDia> diario = new ConcurrentHashMap<>();
    private Function<LocalDate, EstadoDia> porDefecto;

    private static Clock relojA(String fechaHora) {
        Instant instante = LocalDateTime.parse(fechaHora).atZone(MADRID).toInstant();
        return Clock.fixed(instante, MADRID);
    }

    @BeforeEach
    void arranque() {
        perfiles = mock(PerfilService.class);
        horarios = mock(HorarioService.class);
        fichajes = mock(FichajeService.class);
        tablas = mock(TablaSalarialService.class);
        calculo = mock(CalculoConvenioService.class);
        ConvenioCatalog convenios = new ConvenioCatalog(new ObjectMapper());
        servicio = new ResumenMensualService(perfiles, horarios, fichajes, tablas, calculo, convenios, RELOJ);

        diario.clear();
        porDefecto = fecha -> estado(fecha, EstadoDia.Estado.HUECO, -1);

        // Perfil por defecto: cocinero de Madrid, sin salario real (usa el mínimo).
        perfilConSalario(null);
        // El servicio pide el horario de TODO el rango de semanas de una vez
        // (dos consultas, no una por semana): el stub construye el mapa lunes a lunes.
        horarioParaTodoElRango(Optional.of(SEMANA_8H));
        // El servicio pide el diario del periodo de una vez (una consulta, no una
        // por día): el stub construye el mapa del rango con el diario simulado.
        when(fichajes.estadosDelPeriodo(eq(USUARIO), any(), any())).thenAnswer(inv -> {
            LocalDate desde = inv.getArgument(1);
            LocalDate hasta = inv.getArgument(2);
            Map<LocalDate, EstadoDia> estados = new LinkedHashMap<>();
            for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
                estados.put(d, diario.getOrDefault(d, porDefecto.apply(d)));
            }
            return estados;
        });
        when(tablas.salarioBaseMinimo(eq("madrid-hosteleria"), any(), any())).thenReturn(
                Optional.of(new SalarioBaseResuelto(MINIMO_CONVENIO, "EUR/mes",
                        List.of(new Cita("Salario base mínimo 1.250,91 EUR/mes (Art. 20 del convenio)", "https://bocm.es")))));
        when(calculo.importeHorasExtra(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            BigDecimal horas = inv.getArgument(4);
            BigDecimal importe = PRECIO_HORA.multiply(horas).setScale(2, java.math.RoundingMode.HALF_UP);
            return Optional.of(new HorasExtraCalculadas(PRECIO_HORA, importe, desglose(),
                    List.of(new Cita("La hora extra no puede pagarse por debajo de la ordinaria (art. 35.1 ET)", ET_URL))));
        });
        when(calculo.topeHorasExtraAnual(any(), any())).thenReturn(
                new TopeHorasExtra(80, List.of(new Cita("Tope de 80 h extraordinarias al año (art. 35.2 ET)", ET_URL))));
    }

    /** Stub del horario por rango: el mismo horario (o ninguno) para todas las semanas pedidas. */
    private void horarioParaTodoElRango(Optional<HorarioEfectivo> semana) {
        when(horarios.horariosEfectivosDelRango(eq(USUARIO), any(), any())).thenAnswer(inv -> {
            LocalDate desde = inv.getArgument(1);
            LocalDate hasta = inv.getArgument(2);
            Map<LocalDate, Optional<HorarioEfectivo>> semanas = new LinkedHashMap<>();
            for (LocalDate lunes = desde; !lunes.isAfter(hasta); lunes = lunes.plusDays(7)) {
                semanas.put(lunes, semana);
            }
            return semanas;
        });
    }

    private void perfilConSalario(BigDecimal salarioReal) {
        Perfil perfil = new Perfil(USUARIO, "Madrid", "hosteleria", "madrid-hosteleria", "cocinero",
                Map.of("tabla", "general", "nivel", "III", "claseEmpresa", "B"), salarioReal, null,
                OffsetDateTime.parse("2026-01-01T00:00:00+01:00"));
        when(perfiles.busca(USUARIO)).thenReturn(Optional.of(perfil));
    }

    private static EstadoDia estado(LocalDate fecha, EstadoDia.Estado estado, int minutos) {
        return new EstadoDia(fecha, estado, false, fecha.plusDays(15), minutos, List.of());
    }

    private static ValorHoraCalculado desglose() {
        return new ValorHoraCalculado(new BigDecimal("10.8979"), MINIMO_CONVENIO, new BigDecimal("14"),
                BigDecimal.ZERO, new BigDecimal("1800"), false, List.of());
    }

    // --- casos ---

    @Test
    @DisplayName("mes con turno partido y horas extra: suma solo los deltas positivos, con importe y citas")
    void mesConPartidoYExtras() {
        diario.put(LocalDate.of(2026, 7, 6), estado(LocalDate.of(2026, 7, 6), EstadoDia.Estado.COMPLETO, 600)); // lunes partido: +120
        diario.put(LocalDate.of(2026, 7, 7), estado(LocalDate.of(2026, 7, 7), EstadoDia.Estado.COMPLETO, 540)); // +60

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);

        assertThat(r.minutosTeoricos()).isEqualTo(960);
        assertThat(r.minutosReales()).isEqualTo(1140);
        assertThat(r.minutosExtra()).isEqualTo(180);
        assertThat(r.minutosDeficit()).isZero();
        assertThat(r.diasSinCalcular()).isZero();
        assertThat(r.contadoresPorEstado()).containsEntry(EstadoDia.Estado.COMPLETO, 2)
                .containsEntry(EstadoDia.Estado.HUECO, 29);
        assertThat(r.importe().importe()).isEqualByComparingTo("32.70"); // 10,90 × 3 h
        assertThat(r.importe().horasExtra()).isEqualByComparingTo("3.00");
        assertThat(r.importe().salarioRealUsado()).isFalse();
        assertThat(r.importe().salarioBaseAplicado()).isEqualByComparingTo(MINIMO_CONVENIO);
        assertThat(r.importe().citas())
                .anySatisfy(c -> assertThat(c.texto()).contains("Salario base"))
                .anySatisfy(c -> assertThat(c.texto()).contains("art. 35.1"));
        // Sin salario real: la cita del mínimo del convenio SÍ es la base del
        // importe, así que va sin etiqueta de "referencia".
        assertThat(r.importe().citas())
                .filteredOn(c -> c.texto().contains("Salario base"))
                .allSatisfy(c -> assertThat(c.texto()).doesNotContain("Referencia de comparación"));
        assertThat(r.tope().horasTope()).isEqualTo(80);
        assertThat(r.tope().acumuladoAnioHoras()).isEqualByComparingTo("3.00");
        assertThat(r.avisos()).isEmpty();
    }

    @Test
    @DisplayName("mes sin horario (ninguna semana tiene cuadrante) → 422 diciendo que falta el horario")
    void mesSinHorario() {
        horarioParaTodoElRango(Optional.empty());

        assertThatExceptionOfType(ResumenIncompletoException.class)
                .isThrownBy(() -> servicio.delMes(USUARIO, JULIO))
                .withMessageContaining("horario");
    }

    @Test
    @DisplayName("sin N+1 de horario: el rango de semanas se pide UNA vez, nunca semana a semana (review)")
    void horarioSePideUnaVezParaTodoElRango() {
        diario.put(LocalDate.of(2026, 7, 7), estado(LocalDate.of(2026, 7, 7), EstadoDia.Estado.COMPLETO, 540));

        servicio.delMes(USUARIO, JULIO);

        // Una única llamada por rango, del lunes de la semana del 1 de enero al
        // lunes de la semana del fin del mes; y ni una sola llamada por semana.
        verify(horarios).horariosEfectivosDelRango(
                USUARIO, LocalDate.of(2025, 12, 29), LocalDate.of(2026, 7, 27));
        verify(horarios, org.mockito.Mockito.never()).horarioEfectivo(any(), any());
    }

    @Test
    @DisplayName("día COMPLETO 'sin calcular' (techo de cordura, -1): se excluye del agregado y se cuenta en diasSinCalcular")
    void diaSinCalcularExcluidoYContado() {
        diario.put(LocalDate.of(2026, 7, 6), estado(LocalDate.of(2026, 7, 6), EstadoDia.Estado.COMPLETO, 540)); // +60
        diario.put(LocalDate.of(2026, 7, 8), estado(LocalDate.of(2026, 7, 8), EstadoDia.Estado.COMPLETO, -1));   // sin calcular

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);

        assertThat(r.diasSinCalcular()).isEqualTo(1);
        assertThat(r.minutosExtra()).isEqualTo(60);           // solo el día bueno
        assertThat(r.minutosTeoricos()).isEqualTo(480);       // el sin-calcular no suma teórico
        assertThat(r.contadoresPorEstado()).containsEntry(EstadoDia.Estado.COMPLETO, 2);
    }

    @Test
    @DisplayName("delta negativo NO compensa: se informa como déficit aparte, las extra no bajan")
    void deltaNegativoNoCompensa() {
        diario.put(LocalDate.of(2026, 7, 6), estado(LocalDate.of(2026, 7, 6), EstadoDia.Estado.COMPLETO, 600)); // +120
        diario.put(LocalDate.of(2026, 7, 7), estado(LocalDate.of(2026, 7, 7), EstadoDia.Estado.COMPLETO, 360)); // -120

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);

        assertThat(r.minutosExtra()).isEqualTo(120);
        assertThat(r.minutosDeficit()).isEqualTo(120);
        assertThat(r.importe().importe()).isEqualByComparingTo("21.80"); // 10,90 × 2 h extra
    }

    @Test
    @DisplayName("ausencia no computa horas reales: va al contador, no al agregado ni al teórico")
    void ausenciaNoComputa() {
        diario.put(LocalDate.of(2026, 7, 6), estado(LocalDate.of(2026, 7, 6), EstadoDia.Estado.AUSENCIA, -1));
        diario.put(LocalDate.of(2026, 7, 7), estado(LocalDate.of(2026, 7, 7), EstadoDia.Estado.COMPLETO, 540)); // +60

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);

        assertThat(r.contadoresPorEstado()).containsEntry(EstadoDia.Estado.AUSENCIA, 1)
                .containsEntry(EstadoDia.Estado.COMPLETO, 1);
        assertThat(r.minutosTeoricos()).isEqualTo(480); // solo el día computado, la ausencia no suma teórico
        assertThat(r.minutosExtra()).isEqualTo(60);
    }

    @Test
    @DisplayName("D25: si el salario real es MAYOR que el mínimo del convenio, se usa el real para valorar")
    void salarioRealMayorSeUsa() {
        perfilConSalario(new BigDecimal("1400.00")); // > 1250,91
        diario.put(LocalDate.of(2026, 7, 7), estado(LocalDate.of(2026, 7, 7), EstadoDia.Estado.COMPLETO, 540)); // +60

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);

        assertThat(r.importe().salarioRealUsado()).isTrue();
        assertThat(r.importe().salarioBaseAplicado()).isEqualByComparingTo("1400.00");

        ArgumentCaptor<BigDecimal> salarioUsado = ArgumentCaptor.forClass(BigDecimal.class);
        verify(calculo).importeHorasExtra(any(), any(), salarioUsado.capture(), any(), any());
        assertThat(salarioUsado.getValue()).isEqualByComparingTo("1400.00");
    }

    @Test
    @DisplayName("D25/D34: con salario real, la cita del mínimo del convenio se marca como REFERENCIA, no como base del importe (review)")
    void salarioRealEtiquetaLaCitaDelMinimoComoReferencia() {
        perfilConSalario(new BigDecimal("1400.00")); // > 1250,91 → se usa el real
        diario.put(LocalDate.of(2026, 7, 7), estado(LocalDate.of(2026, 7, 7), EstadoDia.Estado.COMPLETO, 540));

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);

        assertThat(r.importe().salarioRealUsado()).isTrue();
        // La cita del mínimo sigue presente (D18, compruébalo) PERO toda mención
        // al mínimo del convenio va etiquetada como referencia de comparación,
        // no como la fuente de la cifra aplicada.
        assertThat(r.importe().citas())
                .filteredOn(c -> c.texto().contains("Salario base mínimo"))
                .isNotEmpty()
                .allSatisfy(c -> assertThat(c.texto()).contains("Referencia de comparación"));
    }

    @Test
    @DisplayName("mes futuro (aún no ha empezado) → 400, no un resumen a cero que se leería como 'no te deben nada' (review)")
    void mesFuturoSeRechaza() {
        YearMonth futuro = YearMonth.of(2027, 3); // el reloj fijo está en agosto de 2026

        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.delMes(USUARIO, futuro))
                .withMessageContaining("todavía no ha empezado");
    }

    @Test
    @DisplayName("el mes en curso (mismo mes que 'hoy') SÍ se resume, recortado hasta hoy")
    void mesEnCursoSeResume() {
        YearMonth agosto = YearMonth.of(2026, 8); // 'hoy' es 15-ago-2026

        // No debe lanzar: es el mes actual, se resume la parte transcurrida.
        ResumenMensual r = servicio.delMes(USUARIO, agosto);

        assertThat(r.mes()).isEqualTo(agosto);
    }

    @Test
    @DisplayName("salario real MENOR que el mínimo: se usa el mínimo del convenio (el convenio es suelo, D25)")
    void salarioRealMenorUsaMinimo() {
        perfilConSalario(new BigDecimal("1000.00")); // < 1250,91
        diario.put(LocalDate.of(2026, 7, 7), estado(LocalDate.of(2026, 7, 7), EstadoDia.Estado.COMPLETO, 540));

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);

        assertThat(r.importe().salarioRealUsado()).isFalse();
        assertThat(r.importe().salarioBaseAplicado()).isEqualByComparingTo(MINIMO_CONVENIO);
    }

    @Test
    @DisplayName("sin perfil → 422 diciendo que falta el perfil")
    void sinPerfil() {
        when(perfiles.busca(USUARIO)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResumenIncompletoException.class)
                .isThrownBy(() -> servicio.delMes(USUARIO, JULIO))
                .withMessageContaining("perfil");
    }

    @Test
    @DisplayName("tabla salarial del convenio pendiente para esas dimensiones → 422 explicando que falta la tabla")
    void tablaPendiente() {
        when(tablas.salarioBaseMinimo(eq("madrid-hosteleria"), any(), any())).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResumenIncompletoException.class)
                .isThrownBy(() -> servicio.delMes(USUARIO, JULIO))
                .withMessageContaining("tabla");
    }

    @Test
    @DisplayName("tabla en otra unidad (no EUR/mes) → 422: no se inventa una conversión")
    void tablaEnOtraUnidad() {
        when(tablas.salarioBaseMinimo(eq("madrid-hosteleria"), any(), any())).thenReturn(
                Optional.of(new SalarioBaseResuelto(new BigDecimal("18000"), "EUR/año", List.of())));

        assertThatExceptionOfType(ResumenIncompletoException.class)
                .isThrownBy(() -> servicio.delMes(USUARIO, JULIO))
                .withMessageContaining("EUR/mes");
    }

    @Test
    @DisplayName("convenio sin jornada/pagas publicadas → 422: no se puede valorar la hora extra")
    void convenioSinJornada() {
        diario.put(LocalDate.of(2026, 7, 7), estado(LocalDate.of(2026, 7, 7), EstadoDia.Estado.COMPLETO, 540));
        // doReturn: re-estubar un thenAnswer con when() lo re-invocaría (args null → NPE).
        org.mockito.Mockito.doReturn(Optional.empty())
                .when(calculo).importeHorasExtra(any(), any(), any(), any(), any());

        assertThatExceptionOfType(ResumenIncompletoException.class)
                .isThrownBy(() -> servicio.delMes(USUARIO, JULIO))
                .withMessageContaining("jornada");
    }

    @Test
    @DisplayName("tope anual SUPERADO: aviso claro con las horas acumuladas del año (D22)")
    void topeAnualSuperado() {
        // Todos los días del año hasta julio como COMPLETO de 16 h (+8 h/día): decenas de horas extra.
        porDefecto = fecha -> estado(fecha, EstadoDia.Estado.COMPLETO, 960);

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);

        assertThat(r.tope().acumuladoAnioHoras()).isGreaterThan(new BigDecimal("80"));
        assertThat(r.avisos()).anySatisfy(a -> assertThat(a).contains("superado"));
    }

    @Test
    @DisplayName("tope anual CERCA (entre el 80% y el 100%): aviso de que se acerca")
    void topeAnualCerca() {
        // 9 días de +8 h = 72 h extra en el año (entre 64 y 80).
        for (int d = 1; d <= 9; d++) {
            LocalDate dia = LocalDate.of(2026, 3, d + 1); // marzo, fuera de julio
            diario.put(dia, estado(dia, EstadoDia.Estado.COMPLETO, 960));
        }

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);

        assertThat(r.tope().acumuladoAnioHoras()).isEqualByComparingTo("72.00");
        assertThat(r.avisos()).anySatisfy(a -> assertThat(a).contains("cerca"));
    }
}
