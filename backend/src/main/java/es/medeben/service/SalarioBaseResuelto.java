package es.medeben.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Salario base mínimo resuelto de la tabla del convenio, con sus citas (D34).
 * OJO {@code unidad}: casi todo el corpus es "EUR/mes", pero hay convenios que
 * publican en otra unidad (Cuenca: "EUR/año") — el consumidor debe comprobarla
 * antes de operar con el importe.
 */
public record SalarioBaseResuelto(BigDecimal importe, String unidad, List<Cita> citas) {

    public SalarioBaseResuelto {
        citas = List.copyOf(citas);
    }
}
