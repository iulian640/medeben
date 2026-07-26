/**
 * El diario de fichajes de la libreta sellada (D38): apuntes append-only con
 * sello del servidor. El estado de un día lo deriva SIEMPRE el backend desde
 * el diario — aquí nunca se calcula ni se inventa nada.
 *
 * El id de usuario sale del JWT en el servidor: no se manda nunca.
 */
import { api } from './api'

export type TipoApunte = 'ENTRADA' | 'SALIDA' | 'AUSENCIA'

/** Jerarquía probatoria del apunte (D38): cuándo se apuntó respecto al momento. */
export type OrigenApunte = 'CONFIRMADO' | 'RECONSTRUIDO' | 'RECTIFICACION_TARDIA'

/**
 * NO_CUADRA (issue #230): los apuntes del día se contradicen y no forman una
 * lectura fiable — el backend lo deriva sin tramos ni total, y la UI debe
 * pedir revisión en vez de enseñar una jornada plausible pero falsa.
 */
export type EstadoDia = 'PENDIENTE' | 'EN_CURSO' | 'COMPLETO' | 'AUSENCIA' | 'HUECO' | 'NO_CUADRA'

export interface ApunteGuardado {
  /** Aditivo (contrato D9): sin el id no se le puede adjuntar nada al apunte, como una ubicación. */
  id: string
  fecha: string
  tipo: TipoApunte
  /** "HH:mm" en entradas y salidas; null en las ausencias. */
  hora: string | null
  /** Solo en ausencias, opcional. NUNCA se renderiza con v-html (RGPD art. 9, D38). */
  motivo: string | null
  origen: OrigenApunte
  /** El sello del servidor: cuándo se apuntó de verdad (ISO con zona). */
  registradoEn: string
}

/** Un tramo cerrado que el motor deriva del diario (horas "HH:mm"). */
export interface TramoDia {
  entrada: string
  salida: string
}

export interface EstadoDiaGuardado {
  fecha: string
  estado: EstadoDia
  sellado: boolean
  /** Cuándo se sella (o se selló) el día: para el contador de la UI. */
  selladoDesde: string
  /**
   * null = "sin calcular", NUNCA un cero inventado. Y no va ligado al estado:
   * un día EN_CURSO puede traer minutos (primer tramo del partido ya cerrado).
   */
  minutosTrabajados: number | null
  /**
   * La LECTURA del diario: los tramos emparejados con las correcciones ya
   * aplicadas. Es lo que la UI enseña como "tu jornada"; los apuntes en bruto
   * son la prueba y se enseñan aparte, plegados.
   */
  tramos: TramoDia[]
  /** La entrada sin salida cuando el día está EN_CURSO; null si no la hay. */
  entradaAbierta: string | null
  apuntes: ApunteGuardado[]
}

/**
 * Cuerpo del POST. `rectificacionTardiaConfirmada` solo tiene efecto sobre
 * días ya sellados (409 sin ella): es el "sé que esto queda registrado como
 * modificación posterior al sellado" que la UI pide con fricción.
 */
export interface ApuntePeticion {
  fecha: string
  tipo: TipoApunte
  hora: string | null
  motivo: string | null
  rectificacionTardiaConfirmada: boolean
}

export const postApunte = (apunte: ApuntePeticion) =>
  api.post<ApunteGuardado>('/fichajes', apunte)

export const getEstadoDia = (fecha: string) =>
  api.get<EstadoDiaGuardado>(`/fichajes/dia/${fecha}`)
