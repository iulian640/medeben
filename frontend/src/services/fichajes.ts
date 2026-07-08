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

export type EstadoDia = 'PENDIENTE' | 'EN_CURSO' | 'COMPLETO' | 'AUSENCIA' | 'HUECO'

export interface ApunteGuardado {
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
