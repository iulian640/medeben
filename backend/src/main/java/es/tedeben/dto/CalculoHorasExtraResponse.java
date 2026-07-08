package es.tedeben.dto;

import java.math.BigDecimal;
import es.tedeben.service.Cita;

import java.util.List;

public record CalculoHorasExtraResponse(
        BigDecimal precioHora,
        BigDecimal importe,
        List<Cita> citas
) {
}
