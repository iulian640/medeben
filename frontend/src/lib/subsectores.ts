/**
 * Opciones de "¿en qué tipo de sitio trabajas?" en lenguaje llano (D15/D20).
 * La clave es la que entiende la API (/convenios/para-trabajador?subsector=).
 */
export interface OpcionSubsector {
  clave: string
  etiqueta: string
}

export const SUBSECTORES: readonly OpcionSubsector[] = [
  { clave: 'hosteleria', etiqueta: 'Bar, restaurante o cafetería' },
  { clave: 'hospedaje', etiqueta: 'Hotel u hospedaje' },
  { clave: 'restauracion-colectiva', etiqueta: 'Comedor de colegio, hospital, empresa...' },
] as const

export function etiquetaSubsector(clave: string): string {
  return SUBSECTORES.find((s) => s.clave === clave)?.etiqueta ?? clave
}
