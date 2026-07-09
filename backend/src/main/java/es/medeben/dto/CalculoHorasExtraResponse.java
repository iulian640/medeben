package es.medeben.dto;

import es.medeben.service.Cita;

import java.math.BigDecimal;
import java.util.List;

public record CalculoHorasExtraResponse(
        BigDecimal precioHora,
        BigDecimal importe,
        DesgloseValorHora desglose,
        List<Cita> citas
) {
}
