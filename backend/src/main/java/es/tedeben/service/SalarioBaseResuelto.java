package es.tedeben.service;

import java.math.BigDecimal;
import java.util.List;

/** Salario base mínimo resuelto de la tabla del convenio, con sus citas (D34). */
public record SalarioBaseResuelto(BigDecimal importe, List<String> citas) {

    public SalarioBaseResuelto {
        citas = List.copyOf(citas);
    }
}
