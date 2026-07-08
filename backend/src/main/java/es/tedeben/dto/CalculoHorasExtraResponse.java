package es.tedeben.dto;

import es.tedeben.service.Cita;

import java.math.BigDecimal;
import java.util.List;

public record CalculoHorasExtraResponse(
        BigDecimal precioHora,
        BigDecimal importe,
        DesgloseValorHora desglose,
        List<Cita> citas
) {
}
