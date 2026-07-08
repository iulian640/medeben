package es.tedeben.service;

import es.tedeben.config.RequiereBaseDeDatos;
import es.tedeben.domain.fichaje.Apunte;
import es.tedeben.domain.fichaje.EstadoDia;
import es.tedeben.domain.fichaje.OrigenApunte;
import es.tedeben.domain.fichaje.TipoApunte;
import es.tedeben.repository.ApunteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

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

    /**
     * Deriva el estado del día emparejando el diario en tramos (D38: turno
     * seguido o partido, máx. {@value #MAX_TRAMOS} tramos).
     *
     * <p>Limitación conocida: los apuntes no llevan identificador de tramo, así
     * que una corrección se asigna POR POSICIÓN — siempre al tramo abierto o,
     * si no lo hay, al último cerrado. Una corrección pensada para un tramo
     * anterior (p.ej. la salida del primer tramo con el segundo ya fichado) se
     * aplicará al tramo equivocado. El techo de cordura
     * ({@link #TRAMO_MAX_MINUTOS}) evita que ese desvío fabrique jornadas de
     * ~24h, pero no recupera la intención: para eso el apunte tendría que
     * declarar a qué tramo corrige.
     */
    @Transactional(readOnly = true)
    public EstadoDia estadoDia(UUID usuarioId, LocalDate fecha) {
        List<Apunte> diario = apuntes.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(usuarioId, fecha);
        boolean sellado = estaSellado(fecha, LocalDate.now(reloj));

        // Emparejado secuencial en tramos: el turno partido (D38, máx. 2 tramos
        // declarados) suma TODOS sus tramos, no solo la última pareja E/S.
        List<Tramo> tramos = new ArrayList<>();
        String entradaAbierta = null;
        Apunte ultimo = null;
        for (Apunte a : diario) {
            if (a.getTipo() == TipoApunte.ENTRADA) {
                if (entradaAbierta == null && tramos.size() >= MAX_TRAMOS) {
                    // Cupo de tramos ya declarado (D38: máx. 2): una entrada más
                    // no abre un tramo fantasma que "reabra" el día — corrige la
                    // entrada del último tramo cerrado (gana la última), igual
                    // que hace la SALIDA con la suya.
                    int i = tramos.size() - 1;
                    tramos.set(i, new Tramo(a.getHora(), tramos.get(i).salida()));
                } else {
                    // Sin tramo abierto (y con cupo libre), abre uno nuevo; con
                    // tramo abierto es una corrección de esa entrada: gana la última.
                    entradaAbierta = a.getHora();
                }
            } else if (a.getTipo() == TipoApunte.SALIDA) {
                if (entradaAbierta != null) {
                    tramos.add(new Tramo(entradaAbierta, a.getHora()));
                    entradaAbierta = null;
                } else if (!tramos.isEmpty()) {
                    // Salida sin tramo abierto: corrección de la salida del último
                    // tramo cerrado (gana la última), no un tramo nuevo.
                    int i = tramos.size() - 1;
                    tramos.set(i, new Tramo(tramos.get(i).entrada(), a.getHora()));
                }
                // Salida sin ninguna entrada previa: no forma tramo; el día
                // quedará PENDIENTE de completar (ver abajo).
            } else if (a.getTipo() == TipoApunte.AUSENCIA) {
                // La ausencia es una frontera: invalida los fichajes anteriores.
                // Corregir después "sí entré" no resucita tramos viejos.
                tramos.clear();
                entradaAbierta = null;
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
        return new EstadoDia(fecha, estado, sellado, selladoDesde(fecha), calculaMinutos(tramos), diario);
    }

    /**
     * Suma de los tramos cerrados, o -1 (sin total) si no hay ninguno o si
     * algún tramo supera el techo de cordura {@link #TRAMO_MAX_MINUTOS}: mejor
     * "sin total" que un día inflado a ~24h por una corrección mal dirigida.
     */
    private static int calculaMinutos(List<Tramo> tramos) {
        if (tramos.isEmpty()) {
            return -1;
        }
        int total = 0;
        for (Tramo t : tramos) {
            int minutos = minutosEntre(t.entrada(), t.salida());
            if (minutos > TRAMO_MAX_MINUTOS) {
                return -1;
            }
            total += minutos;
        }
        return total;
    }

    /** Un tramo cerrado del día (entrada y salida); un turno partido tiene dos. */
    private record Tramo(String entrada, String salida) {
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
}
