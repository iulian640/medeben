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
 */
public record EstadoDiaResponse(
        LocalDate fecha,
        EstadoDia.Estado estado,
        boolean sellado,
        LocalDate selladoDesde,
        Integer minutosTrabajados,
        List<ApunteResponse> apuntes
) {

    public static EstadoDiaResponse desde(EstadoDia e) {
        return new EstadoDiaResponse(e.fecha(), e.estado(), e.sellado(), e.selladoDesde(),
                e.minutosTrabajados() < 0 ? null : e.minutosTrabajados(),
                e.apuntes().stream().map(ApunteResponse::desde).toList());
    }
}
