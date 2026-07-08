package es.tedeben.dto;

import es.tedeben.service.ValorHoraCalculado;

import java.math.BigDecimal;

/** La cuenta del valor hora, para que el usuario vea de dónde sale su mínimo (D35). */
public record DesgloseValorHora(
        BigDecimal salarioBaseMensual,
        BigDecimal mensualidades,
        BigDecimal plusesAnuales,
        BigDecimal jornadaAnualHoras,
        BigDecimal valorHora
) {

    public static DesgloseValorHora desde(ValorHoraCalculado v) {
        return new DesgloseValorHora(
                v.salarioBaseMensual(), v.mensualidades(), v.plusesAnuales(),
                v.jornadaAnualHoras(), v.valorHora());
    }
}
