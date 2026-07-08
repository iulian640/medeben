import { ApiError } from '../services/api'

const FORMATO_IMPORTE = new Intl.NumberFormat('es-ES', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  // El CLDR español no agrupa los miles hasta 5 cifras; un salario de
  // "1.425,50" se lee mejor con el punto de miles siempre. El cast es porque
  // los tipos de la lib de TS aún no conocen el valor 'always' (ES2023).
  useGrouping: 'always' as unknown as boolean,
})

/** 1425.5 → "1.425,50" (estilo español). */
export function formatearImporte(importe: number): string {
  return FORMATO_IMPORTE.format(importe)
}

/**
 * Unidad de la API en lenguaje llano. Las desconocidas se muestran tal cual:
 * nunca inventamos periodicidades (Cuenca publica en EUR/año, no EUR/mes).
 */
const UNIDADES: Record<string, string> = {
  'EUR/mes': '€ al mes',
  'EUR/año': '€ al año',
  'EUR/hora': '€ la hora',
}

export function etiquetaUnidad(unidad: string): string {
  return UNIDADES[unidad] ?? unidad
}

/**
 * Mensaje legible de un error: el backend habla RFC 7807 ({status, detail}),
 * así que el detail va primero. Siempre texto plano, nunca HTML.
 */
export function mensajeDeError(error: unknown): string {
  if (error instanceof ApiError && error.body && typeof error.body === 'object') {
    const detail = (error.body as Record<string, unknown>).detail
    if (typeof detail === 'string' && detail.length > 0) {
      return detail
    }
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Algo ha fallado. Inténtalo de nuevo.'
}

/** Nombres en cristiano de las dimensiones que devuelve la API. */
const ETIQUETAS_DIMENSION: Record<string, string> = {
  claseEmpresa: 'Clase de empresa',
  nivel: 'Nivel',
  grupo: 'Grupo',
  categoria: 'Categoría',
}

export function etiquetaDimension(dimension: string): string {
  const conocida = ETIQUETAS_DIMENSION[dimension]
  if (conocida) {
    return conocida
  }
  // camelCase → "Camel case": mejor que enseñar la clave técnica tal cual.
  const conEspacios = dimension.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase()
  return conEspacios.charAt(0).toUpperCase() + conEspacios.slice(1)
}

/** Explicación corta (una frase) para las preguntas pendientes conocidas. */
const EXPLICACIONES_DIMENSION: Record<string, string> = {
  claseEmpresa:
    'Es la categoría del local según el convenio (por tamaño o tipo). Suele venir en tu nómina o en el cartel del convenio; si dudas, pregunta al encargado o elige la que creas y compara.',
}

export function explicacionDimension(dimension: string): string {
  return EXPLICACIONES_DIMENSION[dimension] ?? ''
}

/**
 * Solo enlazamos URLs http(s): cualquier otro esquema (javascript:, data:...)
 * no se renderiza como enlace. Defensa en profundidad sobre datos del backend.
 */
export function esUrlSegura(url: string | null): url is string {
  return url !== null && /^https?:\/\//i.test(url)
}

/** "2025-12-31" → "31/12/2025". Si no es ISO, se devuelve tal cual. */
export function formatearFecha(valor: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor)
  if (!match) {
    return valor
  }
  return `${match[3]}/${match[2]}/${match[1]}`
}

/** Fecha de hoy en ISO (yyyy-mm-dd) en hora local, para /calculo/salario-base. */
export function hoyIso(): string {
  const hoy = new Date()
  const mes = String(hoy.getMonth() + 1).padStart(2, '0')
  const dia = String(hoy.getDate()).padStart(2, '0')
  return `${hoy.getFullYear()}-${mes}-${dia}`
}
