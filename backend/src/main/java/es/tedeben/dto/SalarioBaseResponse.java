package es.tedeben.dto;

import java.math.BigDecimal;
import es.tedeben.service.Cita;

import java.util.List;

public record SalarioBaseResponse(
        BigDecimal importe,
        String unidad,
        List<Cita> citas
) {
}
