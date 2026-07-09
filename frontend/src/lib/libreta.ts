/**
 * Utilidades de la libreta (D38): horas en zona del navegador, aritmética de
 * fechas ISO independiente de la zona, y los textos en cristiano de estados,
 * tipos y orígenes de apunte.
 */
import type { EstadoDia, OrigenApunte, TipoApunte } from '../services/fichajes'
import type { DiaHorario } from '../services/horario'

/**
 * Flag de "onboarding de la libreta ya visto" en localStorage. SOLO el
 * booleano: ningún dato personal se persiste en el navegador (D38/RGPD).
 */
export const CLAVE_ONBOARDING_LIBRETA = 'medeben.libreta.onboarding-visto'

/**
 * Acceso defensivo al flag: si el navegador no ofrece localStorage (modo
 * privado estricto, WebView capada), el onboarding se enseña cada vez —
 * mejor repetirlo que romper la pantalla.
 */
export function onboardingVisto(): boolean {
  try {
    return globalThis.localStorage?.getItem(CLAVE_ONBOARDING_LIBRETA) === '1'
  } catch {
    return false
  }
}

export function marcaOnboardingVisto(): void {
  try {
    globalThis.localStorage?.setItem(CLAVE_ONBOARDING_LIBRETA, '1')
  } catch {
    // Sin almacenamiento no hay flag que guardar: se volverá a enseñar.
  }
}

const MINUTOS_DIA = 24 * 60

const dosCifras = (n: number) => String(n).padStart(2, '0')

/** La hora actual del navegador en HH:mm, para "entro/salgo ahora". */
export function horaActual(): string {
  const ahora = new Date()
  return `${dosCifras(ahora.getHours())}:${dosCifras(ahora.getMinutes())}`
}

/** Un instante ISO (el sello del servidor) → HH:mm en la zona del navegador. */
export function horaLocalDe(instante: string): string {
  const fecha = new Date(instante)
  return `${dosCifras(fecha.getHours())}:${dosCifras(fecha.getMinutes())}`
}

/** 450 → "7 h 30 min"; 480 → "8 h"; 45 → "45 min"; 0 → "0 min". */
export function formatearMinutos(minutos: number): string {
  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  if (horas === 0) {
    return `${resto} min`
  }
  if (resto === 0) {
    return `${horas} h`
  }
  return `${horas} h ${resto} min`
}

/**
 * Suma días a una fecha ISO (yyyy-mm-dd) operando en UTC: el día de
 * calendario no depende de la zona del navegador ni de los cambios de hora.
 */
export function sumarDias(fechaIso: string, dias: number): string {
  const [anio, mes, dia] = fechaIso.split('-').map(Number)
  const fecha = new Date(Date.UTC(anio, mes - 1, dia + dias))
  return `${fecha.getUTCFullYear()}-${dosCifras(fecha.getUTCMonth() + 1)}-${dosCifras(fecha.getUTCDate())}`
}

/** El lunes de la semana a la que pertenece la fecha (las semanas van de lunes a domingo). */
export function lunesDe(fechaIso: string): string {
  const [anio, mes, dia] = fechaIso.split('-').map(Number)
  const diaSemana = new Date(Date.UTC(anio, mes - 1, dia)).getUTCDay() // 0 = domingo
  return sumarDias(fechaIso, -((diaSemana + 6) % 7))
}

const MILIS_POR_DIA = 24 * 60 * 60 * 1000

/** Días de calendario de una fecha ISO a otra (hasta - desde), en UTC: sin efectos de zona ni de cambio de hora. */
export function diasEntre(desdeIso: string, hastaIso: string): number {
  const [a1, m1, d1] = desdeIso.split('-').map(Number)
  const [a2, m2, d2] = hastaIso.split('-').map(Number)
  return Math.round((Date.UTC(a2, m2 - 1, d2) - Date.UTC(a1, m1 - 1, d1)) / MILIS_POR_DIA)
}

/**
 * El contador de cierre de D38 ("se sella en 3 días"), en cristiano. La fecha
 * absoluta sola no le dice al trabajador si aún llega a tiempo de corregir.
 */
export function cuentaAtrasSello(hoyIso: string, selladoDesdeIso: string): string {
  const dias = diasEntre(hoyIso, selladoDesdeIso)
  if (dias <= 0) {
    return 'hoy'
  }
  if (dias === 1) {
    return 'mañana'
  }
  return `en ${dias} días`
}

const DIAS_SEMANA = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado']

/** "2026-07-08" → "miércoles". El día de la semana de una fecha no depende de la zona. */
export function diaSemanaDe(fechaIso: string): string {
  const [anio, mes, dia] = fechaIso.split('-').map(Number)
  return DIAS_SEMANA[new Date(Date.UTC(anio, mes - 1, dia)).getUTCDay()]
}

/** Estados del día (D38) en lenguaje claro. Los huecos, sin dramatismo. */
export const ETIQUETAS_ESTADO: Record<EstadoDia, string> = {
  PENDIENTE: 'Sin apuntar todavía',
  EN_CURSO: 'En curso: falta la salida',
  COMPLETO: 'Completo',
  AUSENCIA: 'No fuiste, y quedó apuntado',
  HUECO: 'Hueco: quedó sin apuntar',
}

export const ETIQUETAS_TIPO: Record<TipoApunte, string> = {
  ENTRADA: 'Entrada',
  SALIDA: 'Salida',
  AUSENCIA: 'Ausencia',
}

/** El origen del apunte (jerarquía probatoria, D38), dicho en cristiano. */
export const ETIQUETAS_ORIGEN: Record<OrigenApunte, string> = {
  CONFIRMADO: 'fichado al momento',
  RECONSTRUIDO: 'apuntado después',
  RECTIFICACION_TARDIA: 'rectificación tardía',
}

/**
 * Minutos de un tramo del horario; si la salida es anterior a la entrada, el
 * tramo cruza la medianoche (20:00 → 02:00). Mismo criterio que el backend.
 */
function minutosDeTramo(entrada: string, salida: string): number {
  const e = minutosDelDia(entrada)
  const s = minutosDelDia(salida)
  if (s === e) {
    return 0
  }
  return s > e ? s - e : MINUTOS_DIA - e + s
}

function minutosDelDia(hora: string): number {
  const [horas, minutos] = hora.split(':').map(Number)
  return horas * 60 + minutos
}

/** Minutos teóricos de un día del horario (0 = día libre, sin tramos). */
export function minutosTeoricos(dia: DiaHorario): number {
  return dia.tramos.reduce((total, t) => total + minutosDeTramo(t.entrada, t.salida), 0)
}
