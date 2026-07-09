package es.medeben.domain.horario;

import java.util.List;

/**
 * Un día del cuadrante: libre (sin tramos), turno seguido (1 tramo) o partido
 * (2 tramos, máximo — D38).
 */
public record DiaCuadrante(List<Tramo> tramos) {

    public DiaCuadrante {
        tramos = tramos == null ? List.of() : List.copyOf(tramos);
    }
}
