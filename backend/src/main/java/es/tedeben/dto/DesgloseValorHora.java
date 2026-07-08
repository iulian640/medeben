package es.tedeben.dto;

import es.tedeben.service.ValorHoraCalculado;

import java.math.BigDecimal;

/**
 * La cuenta del valor hora, para que el usuario vea de dónde sale su mínimo (D35).
 * `divisorHoras` puede ser la jornada anual o un divisor explícito del convenio
 * (p. ej. las 1.829 h de Tenerife); `esDivisorExplicito` lo distingue para que
 * el frontend no lo etiquete siempre como jornada anual.
 */
public record DesgloseValorHora(
        BigDecimal salarioBaseMensual,
        BigDecimal mensualidades,
        BigDecimal plusesAnuales,
        BigDecimal divisorHoras,
        boolean esDivisorExplicito,
        BigDecimal valorHora
) {

    public static DesgloseValorHora desde(ValorHoraCalculado v) {
        return new DesgloseValorHora(
                v.salarioBaseMensual(), v.mensualidades(), v.plusesAnuales(),
                v.divisorHoras(), v.esDivisorExplicito(), v.valorHora());
    }
}
