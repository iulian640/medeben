package es.medeben.dto;

import es.medeben.domain.horario.Cuadrante;
import es.medeben.domain.horario.DiaCuadrante;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Una versión del cuadrante (semana tipo si {@code semanaInicio} es nulo). */
public record CuadranteResponse(
        LocalDate semanaInicio,
        List<DiaCuadrante> dias,
        OffsetDateTime creadoEn
) {

    public static CuadranteResponse desde(Cuadrante c) {
        return new CuadranteResponse(c.getSemanaInicio(), c.getDias(), c.getCreadoEn());
    }
}
