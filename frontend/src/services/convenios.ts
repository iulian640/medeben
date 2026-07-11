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
  /** true si la tabla del convenio queda por debajo del SMI en cómputo anual. */
  bajoSmi: boolean
  /** SMI de referencia (€/mes, 14 pagas) del año consultado; null si no aplica. */
  smiMensual: number | null
  /**
   * El suelo legal en la MISMA unidad que importe (SMI anual repartido entre
   * las pagas de este convenio, sin pluses). Solo llega cuando bajoSmi: es la
   * cifra que se enseña en grande — la tabla superada sería un dato engañoso.
   */
  minimoLegal: number | null
  /**
   * El caso que PARECE ilegal sin serlo: mensual por debajo del SMI mensual
   * que cumple el cómputo ANUAL (art. 27 ET) gracias a >14 pagas. La UI se
   * adelanta a la duda con estos números. Null si no hay nada que aclarar.
   */
  comparativaSmi: {
    mensualidades: number
    anualConvenio: number
    smiAnual: number
  } | null
  citas: Cita[]
}

export interface DesgloseValorHora {
  salarioBaseMensual: number
  mensualidades: number
  plusesAnuales: number
  /** Divisor aplicado: jornada anual o divisor explícito del convenio (ver flag). */
  divisorHoras: number
  esDivisorExplicito: boolean
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

export const getOcupacion = (
  convenioId: string,
  puestoId: string,
  respuestas: Record<string, string> = {},
) => {
  // Las respuestas ya dadas (tipo de establecimiento, zona, categoría…) van como
  // query params: en los convenios con mapeo condicional revelan la siguiente
  // pregunta encadenada o el nivel ya resuelto por el árbol.
  const qs = new URLSearchParams(respuestas).toString()
  const base = `/convenios/${encodeURIComponent(convenioId)}/puestos/${encodeURIComponent(puestoId)}`
  return api.get<OcupacionResuelta>(qs ? `${base}?${qs}` : base)
}

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
  /** Solo las necesitan los convenios con jornada/pagas por dimensión (la colectiva va por provincia, #231). */
  dimensiones?: Record<string, string>
}) => api.post<HorasExtra>('/calculo/horas-extra', peticion)
