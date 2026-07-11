package es.medeben.dto;

import es.medeben.domain.fichaje.EstadoDia;

import java.time.LocalDate;
import java.util.List;

/**
 * El estado de un día derivado de su diario, con el contador de sellado para la UI.
 *
 * <p>Contrato de {@code minutosTrabajados}: NO va ligado al estado. Un día
 * EN_CURSO puede traer minutos (turno partido con el primer tramo ya cerrado)
 * y un COMPLETO puede traer {@code null} (total no calculable: algún tramo
 * supera el techo de cordura). La UI no debe asumir "minutos solo si COMPLETO".
 * Un día NO_CUADRA (issue #230) trae siempre {@code null}, sin tramos y sin
 * {@code entradaAbierta}: la única verdad que viaja son sus apuntes en bruto.
 *
 * <p>{@code tramos} es la LECTURA del diario (el emparejado derivado, con las
 * correcciones ya aplicadas): lo que la UI enseña como "tu jornada". Los
 * apuntes en bruto siguen viajando como prueba. {@code entradaAbierta} es la
 * hora de la entrada sin salida cuando el día está EN_CURSO; null si no.
 */
public record EstadoDiaResponse(
        LocalDate fecha,
        EstadoDia.Estado estado,
        boolean sellado,
        LocalDate selladoDesde,
        Integer minutosTrabajados,
        List<EstadoDia.TramoDia> tramos,
        String entradaAbierta,
        List<ApunteResponse> apuntes
) {

    public static EstadoDiaResponse desde(EstadoDia e) {
        return new EstadoDiaResponse(e.fecha(), e.estado(), e.sellado(), e.selladoDesde(),
                e.minutosTrabajados() < 0 ? null : e.minutosTrabajados(),
                e.tramos(), e.entradaAbierta(),
                e.apuntes().stream().map(ApunteResponse::desde).toList());
    }
}
