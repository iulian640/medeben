/**
 * "Me deben X € este mes" (D12/D22): el resumen mensual del usuario
 * autenticado. Todo lo calcula el backend comparando su horario con su diario;
 * aquí no se deriva ni se inventa ninguna cifra. Cada importe llega con sus
 * citas (D34, "no me creas, compruébalo").
 */
import { api } from './api'
import type { Cita, DesgloseValorHora } from './convenios'

export interface HorasResumen {
  minutos: number
  horas: number
}

export interface ImporteEstimado {
  horasExtra: number
  precioHora: number
  importe: number
  salarioBaseAplicado: number
  /** true si el importe sale del salario real declarado (D25), no del mínimo del convenio. */
  salarioRealUsado: boolean
  desglose: DesgloseValorHora
  citas: Cita[]
}

export interface TopeAnual {
  horas: number
  acumuladoAnioHoras: number
  citas: Cita[]
}

export interface ResumenMensual {
  mes: string
  minutosTeoricos: number
  minutosReales: number
  horasExtra: HorasResumen
  /** Informativo: las horas de menos NUNCA compensan las extra (D12). */
  deficitInformativo: HorasResumen
  diasSinCalcular: number
  contadoresPorEstado: Record<string, number>
  importeEstimado: ImporteEstimado
  topeAnual: TopeAnual
  avisos: string[]
}

export const getResumenMes = (anyoMes: string) =>
  api.get<ResumenMensual>(`/resumen/mes/${anyoMes}`)
