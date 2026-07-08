package es.tedeben.domain.fichaje;

import java.time.LocalDate;
import java.util.List;

/**
 * El estado de un día, derivado de su diario de apuntes (D38). No se guarda:
 * se calcula siempre desde los apuntes, que son la única verdad.
 *
 * @param minutosTrabajados suma de los tramos cerrados (turno partido incluido,
 *                          D38); -1 si no hay ninguno o si algún tramo supera el
 *                          techo de cordura. OJO: no va ligado al estado — un día
 *                          EN_CURSO con su primer tramo cerrado ya trae minutos,
 *                          y un COMPLETO anómalo puede traer -1.
 * @param selladoDesde      cuándo se sella (o se selló) el día: para el
 *                          contador de la UI ("se sella en 3 días").
 */
public record EstadoDia(
        LocalDate fecha,
        Estado estado,
        boolean sellado,
        LocalDate selladoDesde,
        int minutosTrabajados,
        List<Apunte> apuntes
) {

    public enum Estado {
        /** Sin apuntes y aún dentro de ventana: la app pedirá confirmarlo. */
        PENDIENTE,
        /** Hay un tramo abierto: entrada fichada, salida todavía no. */
        EN_CURSO,
        /** Todos los tramos cerrados (turno seguido o partido, D38). */
        COMPLETO,
        /** No fue a trabajar, registrado. */
        AUSENCIA,
        /** Sellado sin datos: hueco para siempre (un diario real tiene huecos). */
        HUECO
    }
}
