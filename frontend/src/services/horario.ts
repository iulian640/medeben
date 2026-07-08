/**
 * Horario del usuario (D38): semana tipo + ediciones por semana concreta. La
 * libreta solo lo LEE, para comparar lo fichado con las horas teóricas; si el
 * usuario no tiene horario, el GET devuelve 404 y simplemente no se compara.
 */
import { api } from './api'

export interface TramoHorario {
  /** "HH:mm". Si la salida es anterior a la entrada, el tramo cruza la medianoche. */
  entrada: string
  salida: string
}

/** Un día del cuadrante: libre (sin tramos), seguido (1) o partido (2, máx). */
export interface DiaHorario {
  tramos: TramoHorario[]
}

export type OrigenHorario = 'SEMANA_TIPO' | 'SEMANA_EDITADA'

/** El horario que aplica a una semana concreta y de dónde sale. */
export interface HorarioEfectivo {
  /** 7 días, de lunes a domingo. */
  dias: DiaHorario[]
  origen: OrigenHorario
  definidoEn: string
}

export const getHorarioSemana = (lunes: string) =>
  api.get<HorarioEfectivo>(`/horario/semana/${lunes}`)
