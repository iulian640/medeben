package es.tedeben.service;

import java.math.BigDecimal;
import java.util.List;

/** Importe de unas horas extra: precio/hora aplicado, total y citas (D34). */
public record HorasExtraCalculadas(BigDecimal precioHora, BigDecimal importe, List<Cita> citas) {

    public HorasExtraCalculadas {
        citas = List.copyOf(citas);
    }
}
