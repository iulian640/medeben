/**
 * Recordatorio diario de la libreta ("¿has apuntado lo de hoy?"): la lógica
 * pura del plan de notificaciones y la persistencia de la preferencia.
 *
 * Solo se guarda la HORA elegida (un "21:30"), nunca datos personales: la
 * preferencia sobrevive a la sesión igual que el flag del onboarding (D38).
 */
import type { NotificacionProgramada } from './notificaciones'

export const CLAVE_HORA_RECORDATORIO = 'tedeben.libreta.recordatorio-hora'

/**
 * Ids reservados para el recordatorio (reutilizarlos reemplaza en vez de
 * duplicar). Bloque propio para no pisar futuras notificaciones de otra cosa.
 */
export const ID_BASE_RECORDATORIO = 1000

/** Días programados por delante: una quincena; se renueva al abrir la libreta. */
export const DIAS_PROGRAMADOS = 14

/** La hora guardada ("HH:mm") o null si el recordatorio está apagado. */
export function horaRecordatorio(): string | null {
  try {
    return globalThis.localStorage?.getItem(CLAVE_HORA_RECORDATORIO) ?? null
  } catch {
    return null
  }
}

/** Guarda la hora elegida; null la borra (recordatorio apagado). */
export function guardaHoraRecordatorio(hora: string | null): void {
  try {
    if (hora === null) {
      globalThis.localStorage?.removeItem(CLAVE_HORA_RECORDATORIO)
    } else {
      globalThis.localStorage?.setItem(CLAVE_HORA_RECORDATORIO, hora)
    }
  } catch {
    // Sin almacenamiento no hay preferencia que guardar: no es fatal.
  }
}

/** Los ids del bloque del recordatorio, para cancelarlos todos. */
export function idsRecordatorio(): number[] {
  return Array.from({ length: DIAS_PROGRAMADOS }, (_, i) => ID_BASE_RECORDATORIO + i)
}

/**
 * El plan: una notificación al día a la hora elegida durante una quincena,
 * empezando HOY si la hora aún no ha pasado (si ya pasó, desde mañana).
 * Determinista respecto a `ahora` para poder testearlo sin relojes reales.
 */
export function planRecordatorios(hora: string, ahora: Date): NotificacionProgramada[] {
  const [horas, minutos] = hora.split(':').map(Number)
  const primera = new Date(ahora)
  primera.setHours(horas, minutos, 0, 0)
  if (primera.getTime() <= ahora.getTime()) {
    primera.setDate(primera.getDate() + 1)
  }
  return Array.from({ length: DIAS_PROGRAMADOS }, (_, i) => {
    const fecha = new Date(primera)
    fecha.setDate(primera.getDate() + i)
    return {
      id: ID_BASE_RECORDATORIO + i,
      titulo: 'Tu libreta',
      cuerpo: '¿Has apuntado lo de hoy? Entrada y salida, dos toques.',
      fecha,
    }
  })
}
