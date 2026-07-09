/**
 * Aritmética y etiquetas de meses (yyyy-MM) para el resumen mensual.
 * Todo con strings ISO: sin objetos Date, sin efectos de zona horaria.
 */

const NOMBRES_MES = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
]

const dosCifras = (n: number) => String(n).padStart(2, '0')

/** "2026-07-09" → "2026-07". */
export function mesDe(fechaIso: string): string {
  return fechaIso.slice(0, 7)
}

/** Suma meses a un yyyy-MM: sumarMeses('2026-01', -1) → '2025-12'. */
export function sumarMeses(anyoMes: string, meses: number): string {
  const [anyo, mes] = anyoMes.split('-').map(Number)
  const total = anyo * 12 + (mes - 1) + meses
  const nuevoAnyo = Math.floor(total / 12)
  const nuevoMes = (total % 12) + 1
  return `${nuevoAnyo}-${dosCifras(nuevoMes)}`
}

/** "2026-07" → "julio de 2026", para la cabecera del resumen. */
export function etiquetaMes(anyoMes: string): string {
  const [anyo, mes] = anyoMes.split('-').map(Number)
  return `${NOMBRES_MES[mes - 1]} de ${anyo}`
}
