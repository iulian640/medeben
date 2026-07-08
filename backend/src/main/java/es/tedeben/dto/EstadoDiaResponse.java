package es.tedeben.dto;

import es.tedeben.domain.fichaje.EstadoDia;

import java.time.LocalDate;
import java.util.List;

/** El estado de un día derivado de su diario, con el contador de sellado para la UI. */
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
