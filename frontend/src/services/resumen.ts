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
  /** Nombre del convenio del perfil (disclaimer C5, punto 1). */
  convenioNombre: string
  /** Boletín oficial de la fuente del convenio; null si no llega tipado (no se inventa). */
  convenioBoletin: string | null
}

export const getResumenMes = (anyoMes: string) =>
  api.get<ResumenMensual>(`/resumen/mes/${anyoMes}`)

/**
 * El informe mensual en PDF (la evidencia que promete el README): lo genera
 * el backend con el mismo motor que este resumen — aquí solo se descarga.
 *
 * `ubicacion`: la casilla de "incluir ubicación" en la UI, SIEMPRE desmarcada
 * por defecto (contrato §Frontend punto 8). Sin ella, el parámetro ni se
 * manda: el informe de quien nunca activó la feature es EXACTAMENTE el de
 * siempre, byte a byte.
 */
export const getInformeMes = (anyoMes: string, ubicacion = false) =>
  api.getBlob(`/informes/mes/${anyoMes}${ubicacion ? '?ubicacion=true' : ''}`)

/**
 * El anexo técnico con las coordenadas aproximadas (Fase 3 del diseño): el
 * ÚNICO canal por el que salen coordenadas del sistema. Nunca automático,
 * siempre bajo una acción explícita del usuario con su propio aviso en la UI.
 */
export const getAnexoUbicacionMes = (anyoMes: string) =>
  api.getBlob(`/informes/mes/${anyoMes}/anexo-ubicacion`)

/**
 * El histórico anual en PDF: el año mes a mes con totales. El backend lo
 * genera como mucho una vez al día por usuario (caché de 24 h) — el resto
 * del día sirve el mismo documento, con su "generado el..." visible.
 */
export const getInformeAnio = (anio: string) => api.getBlob(`/informes/anio/${anio}`)
