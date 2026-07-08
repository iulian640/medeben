package es.tedeben.dto;

import java.math.BigDecimal;
import java.util.List;

public record CalculoHorasExtraResponse(
        BigDecimal precioHora,
        BigDecimal importe,
        List<String> citas
) {
}
