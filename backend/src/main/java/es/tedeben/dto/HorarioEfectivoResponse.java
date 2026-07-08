package es.tedeben.dto;

import es.tedeben.domain.horario.DiaCuadrante;
import es.tedeben.domain.horario.OrigenHorario;
import es.tedeben.service.HorarioEfectivo;

import java.time.OffsetDateTime;
import java.util.List;

/** El horario que aplica a una semana concreta y de dónde sale (D38). */
public record HorarioEfectivoResponse(
        List<DiaCuadrante> dias,
        OrigenHorario origen,
        OffsetDateTime definidoEn
) {

    public static HorarioEfectivoResponse desde(HorarioEfectivo h) {
        return new HorarioEfectivoResponse(h.dias(), h.origen(), h.definidoEn());
    }
}
