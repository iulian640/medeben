package es.medeben.dto;

import java.math.BigDecimal;
import es.medeben.service.Cita;

import java.util.List;

/**
 * Salario base mínimo del convenio. Si la tabla queda por debajo del SMI vigente
 * ({@code bajoSmi}), la app DEBE avisar: por ley el suelo real es el SMI en
 * cómputo anual, no la tabla del convenio (que pudo quedar congelada por
 * ultraactividad). {@code smiMensual} es el SMI de referencia (€/mes, 14 pagas).
 */
public record SalarioBaseResponse(
        BigDecimal importe,
        String unidad,
        boolean bajoSmi,
        BigDecimal smiMensual,
        List<Cita> citas
) {
}
