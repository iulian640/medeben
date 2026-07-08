package es.tedeben.dto;

import java.math.BigDecimal;
import java.util.List;

public record SalarioBaseResponse(
        BigDecimal importe,
        String unidad,
        List<String> citas
) {
}
