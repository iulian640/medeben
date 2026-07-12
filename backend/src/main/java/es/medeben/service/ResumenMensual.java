package es.medeben.service;

import es.medeben.domain.fichaje.EstadoDia;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * El corazón de la app (D12): "te deben X€ este mes". Agrega el mes día a día
 * comparando los minutos TEÓRICOS del horario efectivo (as-of) con los REALES
 * del diario de fichajes.
 *
 * <p>Reglas (honestidad ante todo):
 * <ul>
 *   <li>{@code minutosExtra} = suma de deltas POSITIVOS por día (real &gt; teórico).</li>
 *   <li>{@code minutosDeficit} = suma de deltas negativos (en positivo): NO compensan
 *       las extra, se informan aparte.</li>
 *   <li>Solo entran al agregado los días con total fiable (COMPLETO con minutos
 *       calculados) y con horario teórico; por eso
 *       {@code minutosExtra - minutosDeficit == minutosReales - minutosTeoricos}.</li>
 *   <li>{@code diasSinCalcular}: días COMPLETO cuyo total quedó "sin calcular"
 *       (techo de cordura de {@code FichajeService}); se excluyen y se cuentan.</li>
 *   <li>Los días NO_CUADRA (issue #230) también se excluyen del agregado, van a
 *       {@code contadoresPorEstado} y generan un aviso con sus fechas: horas
 *       fuera del total, sí, pero nunca en silencio.</li>
 *   <li>Ausencia/hueco/pendiente no computan horas reales (van a los contadores).</li>
 * </ul>
 *
 * <p>{@code convenioNombre} y {@code convenioBoletin} son del convenio del
 * perfil (disclaimer C5, "Cálculo orientativo según las tablas del convenio...
 * "): {@code convenioBoletin} puede ser null si la fuente del convenio no lo
 * trae tipado — nunca se inventa.
 */
public record ResumenMensual(
        YearMonth mes,
        long minutosTeoricos,
        long minutosReales,
        long minutosExtra,
        long minutosDeficit,
        int diasSinCalcular,
        Map<EstadoDia.Estado, Integer> contadoresPorEstado,
        ImporteEstimadoMensual importe,
        TopeAnualResumen tope,
        List<String> avisos,
        String convenioNombre,
        String convenioBoletin
) {

    public ResumenMensual {
        contadoresPorEstado = Map.copyOf(contadoresPorEstado);
        avisos = List.copyOf(avisos);
    }
}
