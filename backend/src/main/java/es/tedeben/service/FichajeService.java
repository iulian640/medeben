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

    @Transactional(readOnly = true)
    public EstadoDia estadoDia(UUID usuarioId, LocalDate fecha) {
        List<Apunte> diario = apuntes.findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(usuarioId, fecha);
        boolean sellado = estaSellado(fecha, LocalDate.now(reloj));

        Apunte ultimaEntrada = null;
        Apunte ultimaSalida = null;
        Apunte ultimo = null;
        for (Apunte a : diario) {
            if (a.getTipo() == TipoApunte.ENTRADA) {
                ultimaEntrada = a;
            } else if (a.getTipo() == TipoApunte.SALIDA) {
                ultimaSalida = a;
            }
            ultimo = a;
        }

        EstadoDia.Estado estado;
        int minutos = -1;
        if (diario.isEmpty()) {
            estado = sellado ? EstadoDia.Estado.HUECO : EstadoDia.Estado.PENDIENTE;
        } else if (ultimo.getTipo() == TipoApunte.AUSENCIA) {
            estado = EstadoDia.Estado.AUSENCIA;
        } else if (ultimaEntrada != null && ultimaSalida != null) {
            estado = EstadoDia.Estado.COMPLETO;
            minutos = minutosEntre(ultimaEntrada.getHora(), ultimaSalida.getHora());
        } else if (ultimaEntrada != null) {
            estado = EstadoDia.Estado.EN_CURSO;
        } else {
            // Salida sin entrada: el día sigue a medias, la app pedirá completarlo.
            estado = EstadoDia.Estado.PENDIENTE;
        }
        return new EstadoDia(fecha, estado, sellado, selladoDesde(fecha), minutos, diario);
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
        if (hora == null || !HORA.matcher(hora).matches()) {
            throw new IllegalArgumentException("Hora inválida — usa HH:mm entre 00:00 y 23:59");
        }
    }

    /** Minutos de un tramo declarado; si la salida es ≤ la entrada, cruza la medianoche. */
    private static int minutosEntre(String entrada, String salida) {
        int e = minutosDelDia(entrada);
        int s = minutosDelDia(salida);
        return s > e ? s - e : MINUTOS_DIA - e + s;
    }

    private static int minutosDelDia(String hora) {
        LocalTime t = LocalTime.parse(hora);
        return t.getHour() * 60 + t.getMinute();
    }
}
