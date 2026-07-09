package es.medeben.service;

import java.math.BigDecimal;
import java.util.List;

/** Importe de unas horas extra: precio/hora aplicado, total, desglose del valor hora y citas (D34/D35). */
public record HorasExtraCalculadas(
        BigDecimal precioHora,
        BigDecimal importe,
        ValorHoraCalculado desglose,
        List<Cita> citas
) {

    public HorasExtraCalculadas {
        citas = List.copyOf(citas);
    }
}
