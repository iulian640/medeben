package es.tedeben.service;

import java.math.BigDecimal;
import java.util.List;

/** Valor de la hora ordinaria con sus citas de artículo (D34: cada dato con su fuente). */
public record ValorHoraCalculado(BigDecimal valorHora, List<Cita> citas) {

    public ValorHoraCalculado {
        citas = List.copyOf(citas);
    }
}
