/**
 * Horario del usuario (D38): semana tipo + ediciones por semana concreta. La
 * libreta lo LEE para comparar lo fichado con las horas teóricas (si no hay
 * horario, el GET devuelve 404 y no se compara), y la pantalla "Tu horario"
 * lo ESCRIBE: la semana tipo con PUT /horario y la edición de una semana
 * concreta con PUT /horario/semana/{lunes}. Todo append-only: cada guardado
 * es una versión nueva y las semanas pasadas conservan la suya.
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

/** Una versión guardada del cuadrante (semana tipo si semanaInicio es null). */
export interface Cuadrante {
  semanaInicio: string | null
  dias: DiaHorario[]
  creadoEn: string
}

export const getHorarioSemana = (lunes: string) =>
  api.get<HorarioEfectivo>(`/horario/semana/${lunes}`)

/** La semana tipo vigente; 404 si el usuario aún no la ha creado. */
export const getSemanaTipo = () => api.get<Cuadrante>('/horario')

export const putSemanaTipo = (dias: DiaHorario[]) =>
  api.put<Cuadrante>('/horario', { dias })

export const putSemana = (lunes: string, dias: DiaHorario[]) =>
  api.put<Cuadrante>(`/horario/semana/${lunes}`, { dias })
