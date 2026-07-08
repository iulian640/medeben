package es.tedeben.service;

import es.tedeben.domain.horario.DiaCuadrante;
import es.tedeben.domain.horario.OrigenHorario;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * El horario que aplica a una semana concreta: la edición de esa semana si la
 * hay, o la semana tipo que estaba vigente entonces (D38).
 */
public record HorarioEfectivo(List<DiaCuadrante> dias, OrigenHorario origen, OffsetDateTime definidoEn) {
}
