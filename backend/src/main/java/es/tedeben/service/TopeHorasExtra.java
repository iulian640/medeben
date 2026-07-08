package es.tedeben.service;

import java.util.List;

/** Tope anual de horas extraordinarias aplicable, con su fuente (convenio o ET). */
public record TopeHorasExtra(int horas, List<Cita> citas) {

    public TopeHorasExtra {
        citas = List.copyOf(citas);
    }
}
