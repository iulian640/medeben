package es.tedeben.service;

import es.tedeben.config.RequiereBaseDeDatos;
import es.tedeben.controller.ResumenIncompletoException;
import es.tedeben.domain.convenio.Convenio;
import es.tedeben.domain.fichaje.EstadoDia;
import es.tedeben.domain.horario.DiaCuadrante;
import es.tedeben.domain.horario.Tramo;
import es.tedeben.domain.usuario.Perfil;
import es.tedeben.repository.ConvenioCatalog;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * "Te deben X€ este mes" (D12/D22): agrega el mes del usuario autenticado
 * comparando, día a día, los minutos TEÓRICOS del horario efectivo (as-of) con
 * los REALES del diario de fichajes ({@link FichajeService#estadoDia}). Las
 * horas extra se valoran con el motor de convenio y el salario del perfil (D25).
 *
 * <p>Si falta un dato necesario (perfil, horario, tabla salarial o jornada/pagas
 * del convenio) no se estima nada: se lanza {@link ResumenIncompletoException}
 * (422) explicando QUÉ falta. Nunca se inventa una cifra (regla de oro).
 *
 * <p>Coste: recorre el año natural hasta el mes para el acumulado del tope, un
 * {@code estadoDia} por día. Es aceptable para v1; si pesa, se optimizará con
 * una consulta por rango.
 */
@Service
@RequiereBaseDeDatos
public class ResumenMensualService {

    private static final int MIN_POR_HORA = 60;
    private static final int MINUTOS_DIA = 24 * MIN_POR_HORA;
    private static final int DIAS_SEMANA = 7;
    private static final String UNIDAD_MENSUAL = "EUR/mes";

    /** Umbral de aviso "cerca del tope": 80% del tope anual (D22). */
    private static final int AVISO_TOPE_NUM = 4;
    private static final int AVISO_TOPE_DEN = 5;

    private final PerfilService perfiles;
    private final HorarioService horarios;
    private final FichajeService fichajes;
    private final TablaSalarialService tablas;
    private final CalculoConvenioService calculo;
    private final ConvenioCatalog convenios;
    private final Clock reloj;

    public ResumenMensualService(PerfilService perfiles, HorarioService horarios,
                                 FichajeService fichajes, TablaSalarialService tablas,
                                 CalculoConvenioService calculo, ConvenioCatalog convenios,
                                 Clock reloj) {
        this.perfiles = perfiles;
        this.horarios = horarios;
        this.fichajes = fichajes;
        this.tablas = tablas;
        this.calculo = calculo;
        this.convenios = convenios;
        this.reloj = reloj;
    }

    public ResumenMensual delMes(UUID usuarioId, YearMonth mes) {
        Perfil perfil = perfiles.busca(usuarioId).orElseThrow(() -> new ResumenIncompletoException(
                "Todavía no has creado tu perfil: sin él no sé tu convenio ni tu salario"));
        Convenio convenio = convenios.porId(perfil.getConvenioId()).orElseThrow(
                () -> new ResumenIncompletoException(
                        "El convenio de tu perfil no está disponible ahora mismo"));

        SalarioAplicado salario = resuelveSalario(perfil, convenio.id(), mes);

        // Cache de horario por semana (lunes). Se comparte entre la comprobación
        // de "hay horario en el mes" y el recorrido día a día del año.
        Map<LocalDate, Optional<HorarioEfectivo>> cacheSemana = new HashMap<>();
        exigeHorarioEnElMes(usuarioId, mes, cacheSemana);

        LocalDate hoy = LocalDate.now(reloj);
        LocalDate finMes = minimo(mes.atEndOfMonth(), hoy);
        LocalDate inicioAnio = mes.atDay(1).withDayOfYear(1);

        Agregado mesAgg = new Agregado();
        Map<EstadoDia.Estado, Integer> contadores = nuevoContador();
        int[] diasSinCalcular = {0};
        long[] extraAnioMin = {0};

        for (LocalDate dia = inicioAnio; !dia.isAfter(finMes); dia = dia.plusDays(1)) {
            boolean enElMes = YearMonth.from(dia).equals(mes);
            EstadoDia estado = fichajes.estadoDia(usuarioId, dia);
            if (enElMes) {
                contadores.merge(estado.estado(), 1, Integer::sum);
            }

            OptionalInt teorico = minutosTeoricos(usuarioId, dia, cacheSemana);
            OptionalInt real = minutosReales(estado);
            if (teorico.isEmpty() || real.isEmpty()) {
                if (enElMes && esSinCalcular(estado)) {
                    diasSinCalcular[0]++;
                }
                continue;
            }

            int delta = real.getAsInt() - teorico.getAsInt();
            if (delta > 0) {
                extraAnioMin[0] += delta;
            }
            if (enElMes) {
                mesAgg.suma(teorico.getAsInt(), real.getAsInt(), delta);
            }
        }

        ImporteEstimadoMensual importe = valora(convenio, mes, salario, mesAgg.extraMin);
        TopeAnualResumen tope = tope(convenio, mes, extraAnioMin[0]);
        List<String> avisos = avisos(tope.horasTope(), extraAnioMin[0]);

        return new ResumenMensual(mes, mesAgg.teoricoMin, mesAgg.realMin, mesAgg.extraMin,
                mesAgg.deficitMin, diasSinCalcular[0], contadores, importe, tope, avisos);
    }

    /** D25: el salario real del perfil si está configurado y es MAYOR que el mínimo del convenio; si no, el mínimo. */
    private SalarioAplicado resuelveSalario(Perfil perfil, String convenioId, YearMonth mes) {
        SalarioBaseResuelto minimo = tablas.salarioBaseMinimo(
                        convenioId, perfil.getDimensiones(), mes.atEndOfMonth())
                .orElseThrow(() -> new ResumenIncompletoException(
                        "Tu convenio no tiene publicada la tabla salarial para tus datos (dimensiones): "
                                + "no puedo estimar tu hora todavía"));
        if (!UNIDAD_MENSUAL.equals(minimo.unidad())) {
            throw new ResumenIncompletoException(
                    "La tabla de tu convenio está en '" + minimo.unidad()
                            + "', no en EUR/mes: aún no sé convertirla para estimar tu mes");
        }
        BigDecimal real = perfil.getSalarioBaseMensual();
        boolean usaReal = real != null && real.compareTo(minimo.importe()) > 0;
        BigDecimal aplicado = usaReal ? real : minimo.importe();
        BigDecimal pluses = perfil.getPlusesAnuales() == null ? BigDecimal.ZERO : perfil.getPlusesAnuales();
        return new SalarioAplicado(aplicado, usaReal, pluses, minimo.citas());
    }

    /** El mes debe tener horario (semana tipo o edición) en al menos una de sus semanas; si no, 422. */
    private void exigeHorarioEnElMes(UUID usuarioId, YearMonth mes,
                                     Map<LocalDate, Optional<HorarioEfectivo>> cache) {
        boolean hayHorario = false;
        LocalDate lunes = lunesDe(mes.atDay(1));
        LocalDate finMes = mes.atEndOfMonth();
        while (!lunes.isAfter(finMes)) {
            if (horarioDeSemana(usuarioId, lunes, cache).isPresent()) {
                hayHorario = true;
            }
            lunes = lunes.plusDays(DIAS_SEMANA);
        }
        if (!hayHorario) {
            throw new ResumenIncompletoException(
                    "No has definido tu horario para ese mes: sin horario no hay horas teóricas que comparar");
        }
    }

    private ImporteEstimadoMensual valora(Convenio convenio, YearMonth mes, SalarioAplicado salario,
                                          long extraMin) {
        BigDecimal horasExtra = minutosAHoras(extraMin);
        HorasExtraCalculadas calculada = calculo.importeHorasExtra(
                        convenio, Year.of(mes.getYear()), salario.importe(), salario.plusesONada(),
                        horasExtraPrecisas(extraMin))
                .orElseThrow(() -> new ResumenIncompletoException(
                        "Tu convenio no tiene publicada la jornada anual o las pagas para "
                                + mes.getYear() + ": no puedo valorar tus horas extra"));

        List<Cita> citas = new ArrayList<>(salario.citasSalario());
        citas.addAll(calculada.citas());
        return new ImporteEstimadoMensual(horasExtra, calculada.precioHora(), calculada.importe(),
                salario.importe(), salario.usaReal(), calculada.desglose(), citas);
    }

    private TopeAnualResumen tope(Convenio convenio, YearMonth mes, long extraAnioMin) {
        TopeHorasExtra tope = calculo.topeHorasExtraAnual(convenio, Year.of(mes.getYear()));
        return new TopeAnualResumen(tope.horas(), minutosAHoras(extraAnioMin), tope.citas());
    }

    private static List<String> avisos(int horasTope, long extraAnioMin) {
        long topeMin = (long) horasTope * MIN_POR_HORA;
        long umbral = topeMin * AVISO_TOPE_NUM / AVISO_TOPE_DEN;
        List<String> avisos = new ArrayList<>();
        if (extraAnioMin >= topeMin) {
            avisos.add("Has superado el tope anual de " + horasTope
                    + " h extraordinarias: llevas " + minutosAHoras(extraAnioMin).toPlainString()
                    + " h este año. Por encima del tope las horas dejan de ser extra ordinarias (art. 35.2 ET).");
        } else if (extraAnioMin >= umbral) {
            avisos.add("Estás cerca del tope anual de " + horasTope + " h extraordinarias: llevas "
                    + minutosAHoras(extraAnioMin).toPlainString() + " h este año.");
        }
        return avisos;
    }

    // --- minutos teóricos (horario efectivo) y reales (diario) ---

    /** Minutos teóricos del día según el horario efectivo de su semana; vacío si esa semana no tiene horario. */
    private OptionalInt minutosTeoricos(UUID usuarioId, LocalDate dia,
                                        Map<LocalDate, Optional<HorarioEfectivo>> cache) {
        Optional<HorarioEfectivo> semana = horarioDeSemana(usuarioId, lunesDe(dia), cache);
        if (semana.isEmpty()) {
            return OptionalInt.empty();
        }
        DiaCuadrante diaCuadrante = semana.get().dias().get(indiceDia(dia));
        int total = 0;
        for (Tramo tramo : diaCuadrante.tramos()) {
            total += minutosTramo(tramo.entrada(), tramo.salida());
        }
        return OptionalInt.of(total);
    }

    /**
     * Minutos reales del día: solo se dan por buenos si el día está COMPLETO y su
     * total está calculado (≥ 0). Ausencia/hueco/pendiente/en curso no computan
     * horas reales; "sin calcular" (-1, techo de cordura) tampoco.
     */
    private static OptionalInt minutosReales(EstadoDia estado) {
        if (estado.estado() != EstadoDia.Estado.COMPLETO || estado.minutosTrabajados() < 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(estado.minutosTrabajados());
    }

    /** Un día COMPLETO cuyo total quedó "sin calcular" (techo de cordura): se excluye y se cuenta (honestidad). */
    private static boolean esSinCalcular(EstadoDia estado) {
        return estado.estado() == EstadoDia.Estado.COMPLETO && estado.minutosTrabajados() < 0;
    }

    private Optional<HorarioEfectivo> horarioDeSemana(UUID usuarioId, LocalDate lunes,
                                                      Map<LocalDate, Optional<HorarioEfectivo>> cache) {
        return cache.computeIfAbsent(lunes, l -> horarios.horarioEfectivo(usuarioId, l));
    }

    private static int minutosTramo(String entrada, String salida) {
        int e = LocalTime.parse(entrada).toSecondOfDay() / MIN_POR_HORA;
        int s = LocalTime.parse(salida).toSecondOfDay() / MIN_POR_HORA;
        // La salida ≤ entrada cruza la medianoche (turno de cierre). HorarioService
        // ya prohíbe entrada == salida al guardar, así que un tramo nunca es de 0.
        return s > e ? s - e : MINUTOS_DIA - e + s;
    }

    private static LocalDate lunesDe(LocalDate dia) {
        return dia.minusDays(indiceDia(dia));
    }

    /** 0 = lunes ... 6 = domingo, igual que el orden de los 7 DiaCuadrante. */
    private static int indiceDia(LocalDate dia) {
        return dia.getDayOfWeek().getValue() - 1;
    }

    private static LocalDate minimo(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static BigDecimal minutosAHoras(long minutos) {
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(MIN_POR_HORA), 2, RoundingMode.HALF_UP);
    }

    /** Horas con más precisión para el producto precio × horas (el motor ya redondea el importe a 2 decimales). */
    private static BigDecimal horasExtraPrecisas(long minutos) {
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(MIN_POR_HORA), 4, RoundingMode.HALF_UP);
    }

    private static Map<EstadoDia.Estado, Integer> nuevoContador() {
        Map<EstadoDia.Estado, Integer> contador = new EnumMap<>(EstadoDia.Estado.class);
        for (EstadoDia.Estado e : EstadoDia.Estado.values()) {
            contador.put(e, 0);
        }
        return contador;
    }

    /** Acumulador mutable del agregado del mes (solo días computables). */
    private static final class Agregado {
        private long teoricoMin;
        private long realMin;
        private long extraMin;
        private long deficitMin;

        void suma(int teorico, int real, int delta) {
            teoricoMin += teorico;
            realMin += real;
            if (delta > 0) {
                extraMin += delta;
            } else {
                deficitMin += -delta;
            }
        }
    }

    /** Salario base aplicado (D25) y pluses del perfil, con las citas de tabla para arrastrar las fuentes al importe. */
    private record SalarioAplicado(BigDecimal importe, boolean usaReal, BigDecimal pluses,
                                   List<Cita> citasSalario) {
        BigDecimal plusesONada() {
            return pluses;
        }
    }
}
