package es.tedeben.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Valor de la hora ordinaria con sus citas (D34) y el desglose de la cuenta
 * (D35: que el usuario entienda de dónde sale):
 * valorHora = (salarioBaseMensual × mensualidades + plusesAnuales) / divisorHoras.
 * `divisorHoras` es el divisor aplicado: la jornada anual del convenio o,
 * si el convenio fija uno explícito (`divisorValorHora.horas`), ese divisor.
 * `esDivisorExplicito` distingue el origen para que el consumidor no etiquete
 * como "jornada anual" un divisor que no lo es (Tenerife no fija jornada anual).
 */
public record ValorHoraCalculado(
        BigDecimal valorHora,
        BigDecimal salarioBaseMensual,
        BigDecimal mensualidades,
        BigDecimal plusesAnuales,
        BigDecimal divisorHoras,
        boolean esDivisorExplicito,
        List<Cita> citas
) {

    public ValorHoraCalculado {
        citas = List.copyOf(citas);
    }
}
