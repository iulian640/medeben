package es.medeben.dto;

import java.math.BigDecimal;
import es.medeben.service.Cita;

import java.util.List;

public record SalarioBaseResponse(
        BigDecimal importe,
        String unidad,
        List<Cita> citas
) {
}
