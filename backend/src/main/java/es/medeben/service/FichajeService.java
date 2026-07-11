package es.medeben.service;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.EstadoDia;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;
import es.medeben.repository.ApunteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * El diario de fichajes de la libreta sellada (D38): apuntes append-only con
 * origen (confirmado / reconstruido / rectificación tardía) y sello del reloj
 * inyectado. El estado de un día se deriva SIEMPRE de su diario.
 */
@Service
@RequiereBaseDeDatos
public class FichajeService {

    /** Un día se sella cuando pasan 14 días desde que acaba: fecha + 1 + 14. */
    static final int DIAS_HASTA_SELLADO = 1 + HorarioService.DIAS_VENTANA_SELLADO;

    /**
     * Hasta cuándo un apunte cuenta como "al momento" (CONFIRMADO): el mediodía
     * del día siguiente. Cubre el turno de cierre fichado de madrugada sin
     * regalar el "confirmado" a lo apuntado ya con el día digerido.
     */
    static final LocalTime FIN_DE_GRACIA = LocalTime.NOON;

    private static final Pattern HORA = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final int MOTIVO_MAX = 200;
    private static final int MINUTOS_DIA = 24 * 60;

    /** D38: como mucho se declaran 2 tramos al día (turno seguido o partido). */
    static final int MAX_TRAMOS = 2;

    /**
     * Techo de cordura por tramo: por encima de 16h casi seguro es una
     * corrección mal dirigida (p.ej. una salida pensada para el primer tramo
     * cerrando el segundo con un cruce de medianoche fantasma), no una jornada
     * real de hostelería.
     */
    static final int TRAMO_MAX_MINUTOS = 16 * 60;

    /** Cota inferior de fecha: más atrás no hay reclamación viva que defender (higiene de datos). */
    static final int ANIOS_ATRAS_MAX = 2;

    private final ApunteRepository apuntes;
    private final Clock reloj;

    public FichajeService(ApunteRepository apuntes, Clock reloj) {
        this.apuntes = apuntes;
        this.reloj = reloj;
    }

    @Transactional
    public Apunte apunta(UUID usuarioId, LocalDate fecha, TipoApunte tipo, String hora,
                         String motivo, boolean rectificacionTardiaConfirmada) {
        LocalDate hoy = LocalDate.now(reloj);
        if (fecha == null || fecha.isAfter(hoy)) {
            throw new IllegalArgumentException("No se puede fichar el futuro");
        }
        if (fecha.isBefore(hoy.minusYears(ANIOS_ATRAS_MAX))) {
            throw new IllegalArgumentException(
                    "No se aceptan apuntes de hace más de " + ANIOS_ATRAS_MAX + " años");
        }
        validaSegunTipo(tipo, hora, motivo);

        OrigenApunte origen;
        if (estaSellado(fecha, hoy)) {
            if (!rectificacionTardiaConfirmada) {
                throw new DiaSelladoException(fecha);
            }
            origen = OrigenApunte.RECTIFICACION_TARDIA;
        } else {
            origen = esAlMomento(fecha) ? OrigenApunte.CONFIRMADO : OrigenApunte.RECONSTRUIDO;
        }
        return apuntes.save(new Apunte(usuarioId, fecha, tipo, hora, motivo, origen,
                OffsetDateTime.now(reloj)));
    }

    /** El estado de un día, derivado de su diario (una consulta por fecha). */
    @Transactional(readOnly = true)
    public EstadoDia estadoDia(UUID usuarioId, LocalDate fecha) {
        List<Apunte> diario = apuntes.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(usuarioId, fecha);
        return derivaEstado(fecha, diario, LocalDate.now(reloj));
    }

    /**
     * El estado de TODOS los días del periodo [desde, hasta] con UNA sola
     * consulta: trae el diario del rango de una vez y deriva cada día en
     * memoria, en vez de una query por día. Así el resumen mensual/anual (D12/
     * D22), que recorre el año natural, no dispara el N+1 (una SELECT por día).
     * Devuelve un estado por cada día del rango, incluidos los días sin apuntes
     * (HUECO si ya sellado, PENDIENTE si no).
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, EstadoDia> estadosDelPeriodo(UUID usuarioId, LocalDate desde, LocalDate hasta) {
        Map<LocalDate, List<Apunte>> porDia = apuntes
                .findByUsuarioIdAndFechaBetweenOrderByFechaAscRegistradoEnAscIdAsc(usuarioId, desde, hasta)
                .stream()
                .collect(Collectors.groupingBy(Apunte::getFecha, LinkedHashMap::new, Collectors.toList()));
        LocalDate hoy = LocalDate.now(reloj);
        Map<LocalDate, EstadoDia> estados = new LinkedHashMap<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            estados.put(dia, derivaEstado(dia, porDia.getOrDefault(dia, List.of()), hoy));
        }
        return estados;
    }

    /**
     * Deriva el estado del día emparejando el diario en tramos (D38: turno
     * seguido o partido, máx. {@value #MAX_TRAMOS} tramos). Puro: no toca BD, así
     * que vale para un día suelto o para el recorrido de un periodo ya cargado.
     *
     * <p>Salida huérfana (issue #221): al reconstruir un día es normal apuntar
     * primero la salida ("me acuerdo de a qué hora salí") y luego la entrada.
     * Una SALIDA sin tramo abierto NI tramos cerrados se recuerda como huérfana y
     * la primera ENTRADA que abriría tramo nuevo la empareja —cierra el tramo
     * (entrada, salida) en vez de dejar el día EN_CURSO con la salida colgando y
     * sus horas fuera del resumen del mes. La AUSENCIA, como frontera, descarta
     * también la huérfana pendiente; una huérfana que nunca se empareja deja el
     * día PENDIENTE, como antes. OJO: una salida sin tramo abierto pero CON tramos
     * cerrados NO es huérfana, es la corrección de la salida del último tramo
     * (regla de posición de abajo).
     *
     * <p>El emparejado es CONSERVADOR: solo se hace cuando es inequívoco, para no
     * fabricar horas que nadie fichó. Dos situaciones ambiguas NO se auto-completan
     * (el día queda EN_CURSO, como antes de la feature, y el usuario lo cierra):
     * (1) la entrada es posterior a la huérfana (cruzaría medianoche): indistinguible
     * de una salida espuria seguida de un turno de tarde aún abierto —el cierre
     * nocturno legítimo se reconstruye "entrada primero" por la vía normal; (2) han
     * llegado dos huérfanas distintas: no se sabe si la segunda corrige a la primera
     * o si son las salidas de dos tramos de un turno partido.
     *
     * <p>Limitación conocida: los apuntes no llevan identificador de tramo, así
     * que una corrección se asigna POR POSICIÓN — siempre al tramo abierto o,
     * si no lo hay, al último cerrado. Una corrección pensada para un tramo
     * anterior (p.ej. la salida del primer tramo con el segundo ya fichado) se
     * aplicará al tramo equivocado. El techo de cordura
     * ({@link #TRAMO_MAX_MINUTOS}) evita que ese desvío —o un emparejado de
     * huérfana del mismo día pero exageradamente largo— fabrique jornadas de ~24h,
     * pero no recupera la intención: para eso el apunte tendría que declarar a
     * qué tramo corrige.
     */
    private EstadoDia derivaEstado(LocalDate fecha, List<Apunte> diario, LocalDate hoy) {
        boolean sellado = estaSellado(fecha, hoy);

        // Emparejado secuencial en tramos: el turno partido (D38, máx. 2 tramos
        // declarados) suma TODOS sus tramos, no solo la última pareja E/S.
        List<EstadoDia.TramoDia> tramos = new ArrayList<>();
        String entradaAbierta = null;
        // Última SALIDA huérfana vista (una salida sin tramo abierto NI tramos
        // cerrados): la esperamos por si llega su entrada después (issue #221).
        String salidaHuerfana = null;
        // ¿Han llegado DOS salidas huérfanas distintas sin resolverse? Entonces el
        // emparejado es ambiguo (¿corrección de la misma salida o dos salidas de un
        // turno partido?) y no auto-completamos: fabricaría un tramo que no se
        // fichó (issue #221). La AUSENCIA, como frontera, lo reinicia.
        boolean huerfanaAmbigua = false;
        Apunte ultimo = null;
        for (Apunte a : diario) {
            if (a.getTipo() == TipoApunte.ENTRADA) {
                if (entradaAbierta == null && tramos.size() >= MAX_TRAMOS) {
                    // Cupo de tramos ya declarado (D38: máx. 2): una entrada más
                    // no abre un tramo fantasma que "reabra" el día — corrige la
                    // entrada del último tramo cerrado (gana la última), igual
                    // que hace la SALIDA con la suya.
                    int i = tramos.size() - 1;
                    tramos.set(i, new EstadoDia.TramoDia(a.getHora(), tramos.get(i).salida()));
                } else if (entradaAbierta == null && salidaHuerfana != null
                        && !huerfanaAmbigua && formaTramoMismoDia(a.getHora(), salidaHuerfana)) {
                    // Hay UNA salida huérfana esperando y esta entrada forma con ella
                    // un tramo del mismo día (entrada < salida): se apuntó la salida
                    // antes que su entrada (flujo real al reconstruir un día: "me
                    // acuerdo de a qué hora salí"). En vez de abrir un tramo que
                    // dejaría el día EN_CURSO con la salida ya registrada colgando
                    // (issue #221), esta entrada la empareja y cierra el tramo.
                    //
                    // OJO a lo que NO empareja: si la entrada es POSTERIOR a la salida
                    // (cruzaría medianoche) no se empareja, porque es indistinguible
                    // de una salida espuria seguida de un turno de tarde aún abierto
                    // —emparejarlas fabricaría horas nocturnas fantasma bajo el techo
                    // de 16h. Y si hubo dos huérfanas distintas (ambiguo), tampoco.
                    // En esos casos la entrada abre tramo y el día queda EN_CURSO,
                    // como antes de la feature; el cierre nocturno legítimo se
                    // reconstruye "entrada primero" por la vía normal de arriba.
                    tramos.add(new EstadoDia.TramoDia(a.getHora(), salidaHuerfana));
                    salidaHuerfana = null;
                } else {
                    // Sin tramo abierto (y con cupo libre), abre uno nuevo; con
                    // tramo abierto es una corrección de esa entrada: gana la última.
                    entradaAbierta = a.getHora();
                }
            } else if (a.getTipo() == TipoApunte.SALIDA) {
                if (entradaAbierta != null) {
                    tramos.add(new EstadoDia.TramoDia(entradaAbierta, a.getHora()));
                    entradaAbierta = null;
                } else if (!tramos.isEmpty()) {
                    // Salida sin tramo abierto pero con tramos cerrados: corrección
                    // de la salida del último tramo (gana la última), NO una
                    // huérfana — por eso este caso va antes que el de abajo.
                    int i = tramos.size() - 1;
                    tramos.set(i, new EstadoDia.TramoDia(tramos.get(i).entrada(), a.getHora()));
                } else {
                    // Salida sin tramo abierto NI tramos cerrados: huérfana. La
                    // recordamos para emparejarla cuando llegue su entrada, en vez de
                    // descartarla en silencio (issue #221). Si nunca llega esa
                    // entrada, el día queda PENDIENTE, exactamente como antes.
                    //
                    // Si YA había una huérfana pendiente, ahora hay dos salidas
                    // sueltas distintas: no sabemos si la segunda corrige a la primera
                    // o si son las salidas de dos tramos de un turno partido, así que
                    // marcamos el emparejado como ambiguo y no auto-completamos
                    // (issue #221: evita fabricar un tramo mezclado que nadie fichó).
                    if (salidaHuerfana != null) {
                        huerfanaAmbigua = true;
                    }
                    salidaHuerfana = a.getHora();
                }
            } else if (a.getTipo() == TipoApunte.AUSENCIA) {
                // La ausencia es una frontera: invalida los fichajes anteriores.
                // Corregir después "sí entré" no resucita tramos viejos, ni tampoco
                // la huérfana pendiente (queda descartada como todo lo anterior).
                tramos.clear();
                entradaAbierta = null;
                salidaHuerfana = null;
                huerfanaAmbigua = false;
            }
            ultimo = a;
        }

        EstadoDia.Estado estado;
        if (diario.isEmpty()) {
            estado = sellado ? EstadoDia.Estado.HUECO : EstadoDia.Estado.PENDIENTE;
        } else if (ultimo.getTipo() == TipoApunte.AUSENCIA) {
            estado = EstadoDia.Estado.AUSENCIA;
        } else if (entradaAbierta != null) {
            estado = EstadoDia.Estado.EN_CURSO;
        } else if (!tramos.isEmpty()) {
            estado = EstadoDia.Estado.COMPLETO;
        } else {
            // Salida sin entrada: el día sigue a medias, la app pedirá completarlo.
            estado = EstadoDia.Estado.PENDIENTE;
        }
        return new EstadoDia(fecha, estado, sellado, selladoDesde(fecha), calculaMinutos(tramos),
                tramos, entradaAbierta, diario);
    }

    /**
     * Suma de los tramos cerrados, o -1 (sin total) si no hay ninguno o si
     * algún tramo supera el techo de cordura {@link #TRAMO_MAX_MINUTOS}: mejor
     * "sin total" que un día inflado a ~24h por una corrección mal dirigida.
     */
    private static int calculaMinutos(List<EstadoDia.TramoDia> tramos) {
        if (tramos.isEmpty()) {
            return -1;
        }
        int total = 0;
        for (EstadoDia.TramoDia t : tramos) {
            int minutos = minutosEntre(t.entrada(), t.salida());
            if (minutos > TRAMO_MAX_MINUTOS) {
                return -1;
            }
            total += minutos;
        }
        return total;
    }

    private boolean estaSellado(LocalDate fecha, LocalDate hoy) {
        return !hoy.isBefore(selladoDesde(fecha));
    }

    private static LocalDate selladoDesde(LocalDate fecha) {
        return fecha.plusDays(DIAS_HASTA_SELLADO);
    }

    /** "Al momento" = antes del mediodía siguiente al día del turno (turno de cierre incluido). */
    private boolean esAlMomento(LocalDate fecha) {
        ZonedDateTime limite = fecha.plusDays(1).atTime(FIN_DE_GRACIA).atZone(reloj.getZone());
        return ZonedDateTime.now(reloj).isBefore(limite);
    }

    private static void validaSegunTipo(TipoApunte tipo, String hora, String motivo) {
        if (tipo == null) {
            throw new IllegalArgumentException("Falta el tipo de apunte");
        }
        if (tipo == TipoApunte.AUSENCIA) {
            if (hora != null) {
                throw new IllegalArgumentException("Una ausencia no lleva hora");
            }
            if (motivo != null && motivo.length() > MOTIVO_MAX) {
                throw new IllegalArgumentException("El motivo no puede pasar de " + MOTIVO_MAX + " caracteres");
            }
            return;
        }
        if (motivo != null) {
            // Minimización RGPD (art. 25): el texto libre —que puede ser dato de
            // salud, art. 9— solo existe donde hace falta, en las ausencias.
            throw new IllegalArgumentException("El motivo solo se admite en las ausencias");
        }
        if (hora == null || !HORA.matcher(hora).matches()) {
            throw new IllegalArgumentException("Hora inválida — usa HH:mm entre 00:00 y 23:59");
        }
    }

    /**
     * Minutos de un tramo declarado; si la salida es anterior a la entrada,
     * cruza la medianoche. Entrada y salida iguales cuentan 0 (doble toque o
     * corrección errónea), no un día entero.
     */
    private static int minutosEntre(String entrada, String salida) {
        int e = minutosDelDia(entrada);
        int s = minutosDelDia(salida);
        if (s == e) {
            return 0;
        }
        return s > e ? s - e : MINUTOS_DIA - e + s;
    }

    private static int minutosDelDia(String hora) {
        LocalTime t = LocalTime.parse(hora);
        return t.getHour() * 60 + t.getMinute();
    }

    /**
     * ¿La entrada y la salida forman un tramo del MISMO día (entrada estrictamente
     * antes que salida, sin cruce de medianoche)? Solo así vale emparejar una
     * salida huérfana con una entrada posterior (issue #221): un emparejado que
     * cruzara medianoche es indistinguible de una salida espuria seguida de un
     * turno todavía abierto, y fabricaría horas nocturnas fantasma.
     */
    private static boolean formaTramoMismoDia(String entrada, String salida) {
        return minutosDelDia(entrada) < minutosDelDia(salida);
    }
}
