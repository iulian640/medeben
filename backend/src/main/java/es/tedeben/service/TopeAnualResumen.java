package es.tedeben.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * El tope anual de horas extra (D22: avisar al acercarse/superarlo) y las horas
 * extra ya acumuladas en el año natural hasta el mes consultado. El acumulado es
 * INFORMATIVO y best-effort: recorre el año con la misma lógica día a día, pero
 * los días sin horario o sin total fiable no cuentan.
 */
public record TopeAnualResumen(int horasTope, BigDecimal acumuladoAnioHoras, List<Cita> citas) {

    public TopeAnualResumen {
        citas = List.copyOf(citas);
    }
}
