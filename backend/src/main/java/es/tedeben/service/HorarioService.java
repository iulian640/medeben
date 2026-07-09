package es.tedeben.service;

import es.tedeben.config.RequiereBaseDeDatos;
import es.tedeben.domain.horario.Cuadrante;
import es.tedeben.domain.horario.DiaCuadrante;
import es.tedeben.domain.horario.OrigenHorario;
import es.tedeben.domain.horario.Tramo;
import es.tedeben.repository.CuadranteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Horario del usuario: semana tipo que se repite + ediciones por semana
 * concreta, todo append-only (D38). El horario efectivo de una semana pasada
 * usa la versión que estaba vigente ENTONCES: nadie reescribe el pasado.
 */
@Service
@RequiereBaseDeDatos
public class HorarioService {

    /** Zona de referencia del sellado. Pendiente: Canarias (UTC-1) cuando haya zona por perfil. */
    static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    /** Días desde el FIN de la semana hasta que se sella (D38: ventana de confirmación). */
    static final int DIAS_VENTANA_SELLADO = 14;

    /** Hasta cuántas semanas de antelación se puede editar una semana concreta. */
    static final int SEMANAS_FUTURO_MAX = 8;

    private static final Pattern HORA = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final int DIAS_SEMANA = 7;
    private static final int MAX_TRAMOS_POR_DIA = 2;

    private final CuadranteRepository cuadrantes;
    private final Clock reloj;

    public HorarioService(CuadranteRepository cuadrantes, Clock reloj) {
        this.cuadrantes = cuadrantes;
        this.reloj = reloj;
    }

    @Transactional
    public Cuadrante guardaSemanaTipo(UUID usuarioId, List<DiaCuadrante> dias) {
        validaSemana(dias);
        return cuadrantes.save(new Cuadrante(usuarioId, null, dias, OffsetDateTime.now(reloj)));
    }

    @Transactional
    public Cuadrante guardaSemana(UUID usuarioId, LocalDate lunes, List<DiaCuadrante> dias) {
        exigeLunes(lunes);
        if (estaSellada(lunes)) {
            throw new SemanaSelladaException(lunes);
        }
        // Hacia atrás acota el sellado; hacia delante, un tope razonable: los
        // cuadrantes reales se conocen con días o semanas de antelación, no meses.
        if (lunes.isAfter(LocalDate.now(reloj).plusWeeks(SEMANAS_FUTURO_MAX))) {
            throw new IllegalArgumentException(
                    "Esa semana está a más de " + SEMANAS_FUTURO_MAX + " semanas vista; para el horario habitual usa la semana tipo");
        }
        validaSemana(dias);
        return cuadrantes.save(new Cuadrante(usuarioId, lunes, dias, OffsetDateTime.now(reloj)));
    }

    @Transactional(readOnly = true)
    public Optional<HorarioEfectivo> horarioEfectivo(UUID usuarioId, LocalDate lunes) {
        exigeLunes(lunes);
        Optional<Cuadrante> edicion =
                cuadrantes.findTopByUsuarioIdAndSemanaInicioOrderByCreadoEnDescIdDesc(usuarioId, lunes);
        if (edicion.isPresent()) {
            Cuadrante c = edicion.get();
            return Optional.of(new HorarioEfectivo(c.getDias(), OrigenHorario.SEMANA_EDITADA, c.getCreadoEn()));
        }
        // La semana tipo vigente al ACABAR esa semana: una versión creada después
        // no aplica retroactivamente (el pasado no se reescribe, D38).
        OffsetDateTime finDeSemana = lunes.plusDays(DIAS_SEMANA).atStartOfDay(ZONA).toOffsetDateTime();
        return cuadrantes
                .findTopByUsuarioIdAndSemanaInicioIsNullAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(usuarioId, finDeSemana)
                .map(c -> new HorarioEfectivo(c.getDias(), OrigenHorario.SEMANA_TIPO, c.getCreadoEn()));
    }

    @Transactional(readOnly = true)
    public Optional<Cuadrante> semanaTipoActual(UUID usuarioId) {
        return cuadrantes.findTopByUsuarioIdAndSemanaInicioIsNullOrderByCreadoEnDescIdDesc(usuarioId);
    }

    /**
     * Horario efectivo de TODAS las semanas de un rango de lunes (ambos incluidos)
     * en DOS consultas: las ediciones del rango y las versiones de la semana tipo.
     * La resolución por semana es en memoria con la MISMA semántica que
     * {@link #horarioEfectivo}: la edición gana, y si no la hay aplica la semana
     * tipo vigente al ACABAR esa semana (el pasado no se reescribe, D38). Evita
     * el N+1 de pedir semana a semana en recorridos largos (p. ej. el año del
     * resumen mensual: ~52 semanas serían ~104 consultas).
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, Optional<HorarioEfectivo>> horariosEfectivosDelRango(
            UUID usuarioId, LocalDate lunesDesde, LocalDate lunesHasta) {
        exigeLunes(lunesDesde);
        exigeLunes(lunesHasta);
        if (lunesHasta.isBefore(lunesDesde)) {
            throw new IllegalArgumentException("El rango de semanas está del revés");
        }

        // Más reciente primero + putIfAbsent = se queda la última versión de cada semana.
        Map<LocalDate, Cuadrante> ediciones = new HashMap<>();
        for (Cuadrante c : cuadrantes.findByUsuarioIdAndSemanaInicioBetweenOrderByCreadoEnDescIdDesc(
                usuarioId, lunesDesde, lunesHasta)) {
            ediciones.putIfAbsent(c.getSemanaInicio(), c);
        }

        // Todas las versiones de la semana tipo que pudieran aplicar a alguna
        // semana del rango, más reciente primero: para cada semana vale la
        // primera creada antes de su fin de semana.
        OffsetDateTime finUltimaSemana =
                lunesHasta.plusDays(DIAS_SEMANA).atStartOfDay(ZONA).toOffsetDateTime();
        List<Cuadrante> tipos =
                cuadrantes.findByUsuarioIdAndSemanaInicioIsNullAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(
                        usuarioId, finUltimaSemana);

        Map<LocalDate, Optional<HorarioEfectivo>> resultado = new LinkedHashMap<>();
        for (LocalDate lunes = lunesDesde; !lunes.isAfter(lunesHasta); lunes = lunes.plusDays(DIAS_SEMANA)) {
            resultado.put(lunes, resuelveSemana(lunes, ediciones, tipos));
        }
        return resultado;
    }

    private static Optional<HorarioEfectivo> resuelveSemana(LocalDate lunes,
                                                            Map<LocalDate, Cuadrante> ediciones,
                                                            List<Cuadrante> tipos) {
        Cuadrante edicion = ediciones.get(lunes);
        if (edicion != null) {
            return Optional.of(new HorarioEfectivo(
                    edicion.getDias(), OrigenHorario.SEMANA_EDITADA, edicion.getCreadoEn()));
        }
        OffsetDateTime finDeSemana = lunes.plusDays(DIAS_SEMANA).atStartOfDay(ZONA).toOffsetDateTime();
        for (Cuadrante tipo : tipos) {
            if (tipo.getCreadoEn().isBefore(finDeSemana)) {
                return Optional.of(new HorarioEfectivo(
                        tipo.getDias(), OrigenHorario.SEMANA_TIPO, tipo.getCreadoEn()));
            }
        }
        return Optional.empty();
    }

    private boolean estaSellada(LocalDate lunes) {
        LocalDate selladaDesde = lunes.plusDays(DIAS_SEMANA + DIAS_VENTANA_SELLADO);
        return !LocalDate.now(reloj).isBefore(selladaDesde);
    }

    private static void exigeLunes(LocalDate fecha) {
        if (fecha == null || fecha.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("La semana se identifica por su lunes");
        }
    }

    private static void validaSemana(List<DiaCuadrante> dias) {
        if (dias == null || dias.size() != DIAS_SEMANA) {
            throw new IllegalArgumentException("El horario debe tener exactamente 7 días, de lunes a domingo");
        }
        for (int i = 0; i < dias.size(); i++) {
            validaDia(dias.get(i), i);
        }
    }

    private static void validaDia(DiaCuadrante dia, int indice) {
        if (dia == null) {
            throw new IllegalArgumentException("Día " + (indice + 1) + ": falta el día (un día libre son cero tramos)");
        }
        List<Tramo> tramos = dia.tramos();
        if (tramos.size() > MAX_TRAMOS_POR_DIA) {
            throw new IllegalArgumentException(
                    "Día " + (indice + 1) + ": máximo " + MAX_TRAMOS_POR_DIA + " tramos (turno partido)");
        }
        LocalTime salidaAnterior = null;
        for (int t = 0; t < tramos.size(); t++) {
            Tramo tramo = tramos.get(t);
            LocalTime entrada = parseHora(tramo.entrada(), indice);
            LocalTime salida = parseHora(tramo.salida(), indice);
            if (entrada.equals(salida)) {
                throw new IllegalArgumentException("Día " + (indice + 1) + ": un tramo no puede empezar y acabar a la misma hora");
            }
            boolean cruzaMedianoche = salida.isBefore(entrada);
            if (cruzaMedianoche && t < tramos.size() - 1) {
                throw new IllegalArgumentException(
                        "Día " + (indice + 1) + ": solo el último tramo del día puede cruzar la medianoche");
            }
            if (salidaAnterior != null && entrada.isBefore(salidaAnterior)) {
                throw new IllegalArgumentException("Día " + (indice + 1) + ": los tramos se solapan");
            }
            salidaAnterior = salida;
        }
    }

    private static LocalTime parseHora(String hora, int indiceDia) {
        if (hora == null || !HORA.matcher(hora).matches()) {
            throw new IllegalArgumentException(
                    "Día " + (indiceDia + 1) + ": hora inválida '" + hora + "' — usa HH:mm entre 00:00 y 23:59");
        }
        return LocalTime.parse(hora);
    }
}
