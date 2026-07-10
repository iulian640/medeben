package es.medeben.domain.horario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Descanso entre jornadas (art. 34.3 ET + RD 1561/1995): entre el fin de una
 * jornada y el inicio de la siguiente deben mediar, como regla general, 12 horas.
 * En hostelería, ante un cambio de turno, puede reducirse hasta un mínimo de 7 h,
 * pero <b>la diferencia hasta las 12 h debe compensarse con descanso equivalente</b>
 * en los días siguientes. Por debajo de 7 h no es lícito ni con compensación.
 *
 * <p>Este cálculo trabaja sobre el CUADRANTE teórico: mira, para cada par de días
 * trabajados consecutivos, el hueco real (con fecha y hora, respetando los turnos
 * de cierre que cruzan la medianoche) entre la salida del último tramo de un día y
 * la entrada del primer tramo del día siguiente. Solo informa; nunca inventa.
 */
public final class DescansoEntreJornadas {

    /** Descanso ordinario entre jornadas (art. 34.3 ET). */
    static final int ORDINARIO_MINUTOS = 12 * 60;
    /** Suelo del descanso en hostelería con cambio de turno (RD 1561/1995). */
    static final int MINIMO_HOSTELERIA_MINUTOS = 7 * 60;

    private DescansoEntreJornadas() {
    }

    /**
     * Una jornada que empezó sin respetar las 12 h de descanso desde la anterior.
     *
     * @param fecha              día cuyo inicio de jornada no respetó el descanso
     * @param minutosDescanso    hueco real de descanso desde el fin de la jornada anterior
     * @param minutosCompensables lo que el convenio debe devolver como descanso (12 h − hueco)
     * @param bajoMinimoLegal    true si el hueco es menor de 7 h: ilícito aun compensándolo
     */
    public record Incidencia(LocalDate fecha, int minutosDescanso, int minutosCompensables,
                             boolean bajoMinimoLegal) {
    }

    /**
     * Incidencias de descanso insuficiente. {@code dias} son los días del cuadrante
     * en orden ascendente de fecha (pueden incluir días libres, sin tramos, que se
     * ignoran). Solo se comparan días TRABAJADOS consecutivos en la lista.
     */
    public static List<Incidencia> incidencias(List<Map.Entry<LocalDate, DiaCuadrante>> dias) {
        List<Incidencia> incidencias = new ArrayList<>();
        LocalDateTime finJornadaAnterior = null;
        for (Map.Entry<LocalDate, DiaCuadrante> dia : dias) {
            List<Tramo> tramos = dia.getValue().tramos();
            if (tramos.isEmpty()) {
                continue; // día libre: no rompe la cadena, pero tampoco tiene jornada
            }
            LocalDateTime inicioJornada = inicioJornada(dia.getKey(), tramos.get(0));
            if (finJornadaAnterior != null) {
                long hueco = java.time.Duration.between(finJornadaAnterior, inicioJornada).toMinutes();
                // Un hueco negativo (solapes imposibles del cuadrante) no cuenta; por
                // encima de 12 h el descanso ya se respeta. Entre ambos, incidencia.
                if (hueco >= 0 && hueco < ORDINARIO_MINUTOS) {
                    incidencias.add(new Incidencia(dia.getKey(), (int) hueco,
                            ORDINARIO_MINUTOS - (int) hueco, hueco < MINIMO_HOSTELERIA_MINUTOS));
                }
            }
            finJornadaAnterior = finJornada(dia.getKey(), tramos.get(tramos.size() - 1));
        }
        return incidencias;
    }

    /** Inicio de la jornada: entrada del primer tramo, ese mismo día. */
    private static LocalDateTime inicioJornada(LocalDate fecha, Tramo primero) {
        return fecha.atTime(LocalTime.parse(primero.entrada()));
    }

    /** Fin de la jornada: salida del último tramo; si cruza la medianoche, cae al día siguiente. */
    private static LocalDateTime finJornada(LocalDate fecha, Tramo ultimo) {
        LocalTime entrada = LocalTime.parse(ultimo.entrada());
        LocalTime salida = LocalTime.parse(ultimo.salida());
        LocalDate diaSalida = salida.isAfter(entrada) ? fecha : fecha.plusDays(1);
        return diaSalida.atTime(salida);
    }
}
