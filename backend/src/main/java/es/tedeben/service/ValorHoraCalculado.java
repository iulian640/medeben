package es.tedeben.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Valor de la hora ordinaria con sus citas (D34) y el desglose de la cuenta
 * (D35: que el usuario entienda de dónde sale):
 * valorHora = (salarioBaseMensual × mensualidades + plusesAnuales) / jornadaAnualHoras.
 */
public record ValorHoraCalculado(
        BigDecimal valorHora,
        BigDecimal salarioBaseMensual,
        BigDecimal mensualidades,
        BigDecimal plusesAnuales,
        BigDecimal jornadaAnualHoras,
        List<Cita> citas
) {

    public ValorHoraCalculado {
        citas = List.copyOf(citas);
    }
}
