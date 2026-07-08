/**
 * Endpoints públicos de convenios y cálculos (sin datos personales).
 * Tipos calcados de los DTOs del backend (ConvenioResumenDto, OcupacionResuelta...).
 */
import { api } from './api'

/** Cada dato con su cita y enlace al boletín oficial (D34/D18). */
export interface Cita {
  texto: string
  url: string | null
}

export interface ConvenioResumen {
  id: string
  nombre: string
  subsector: string
  ambitoTipo: string
  provincias: string[]
  vigenciaDesde: string
  vigenciaHasta: string
  fuenteUrl: string | null
  estado: string | null
}

export interface Puesto {
  id: string
  etiqueta: string
}

export interface OpcionDimension {
  dimension: string
  valores: string[]
}

export interface OcupacionResuelta {
  dimensiones: Record<string, string>
  pendientes: OpcionDimension[]
  articulo: string | null
}

export interface SalarioBase {
  importe: number
  unidad: string
  citas: Cita[]
}

export interface DesgloseValorHora {
  salarioBaseMensual: number
  mensualidades: number
  plusesAnuales: number
  jornadaAnualHoras: number
  valorHora: number
}

export interface HorasExtra {
  precioHora: number
  importe: number
  desglose: DesgloseValorHora
  citas: Cita[]
}

export const getProvincias = () => api.get<string[]>('/provincias')

export const getConvenioParaTrabajador = (provincia: string, subsector: string) =>
  api.get<ConvenioResumen>(
    `/convenios/para-trabajador?provincia=${encodeURIComponent(provincia)}&subsector=${encodeURIComponent(subsector)}`,
  )

export const getPuestos = () => api.get<Puesto[]>('/puestos')

export const getOcupacion = (convenioId: string, puestoId: string) =>
  api.get<OcupacionResuelta>(
    `/convenios/${encodeURIComponent(convenioId)}/puestos/${encodeURIComponent(puestoId)}`,
  )

export const postSalarioBase = (
  convenioId: string,
  dimensiones: Record<string, string>,
  fecha: string,
) => api.post<SalarioBase>('/calculo/salario-base', { convenioId, fecha, dimensiones })

export const postHorasExtra = (peticion: {
  convenioId: string
  anio: number
  salarioBaseMensual: number
  plusesAnuales: number
  horas: number
}) => api.post<HorasExtra>('/calculo/horas-extra', peticion)
