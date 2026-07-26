/**
 * "Anotar dónde fichas" (opt-in, apagada de fábrica): HTTP + el flujo
 * compuesto de adjuntar la posición a un fichaje ya guardado.
 *
 * El id de usuario sale del JWT en el servidor: no se manda nunca.
 * Las coordenadas del centro NUNCA se piden por GET aquí a propósito
 * (D3 del diseño: ninguna coordenada sale en una respuesta de API de uso
 * ordinario); `CentroTrabajoGuardado` no las lleva.
 */
import { api, ApiError } from './api'
import { capturaPosicion, registraIntentoUbicacion, type Posicion } from '../lib/ubicacion'

/** Texto canónico versionado (docs/legal/consentimiento-ubicacion-v1.0.md, fuera de este lane). */
export const VERSION_CONSENTIMIENTO_UBICACION = '1.0'

const CLAVE_UBICACION_ACTIVA = 'medeben.ubicacion.activa'
const CLAVE_AVISO_CONSENTIMIENTO_CADUCADO = 'medeben.ubicacion.aviso-consentimiento-caducado'

export interface CentroTrabajoGuardado {
  alias: string | null
  radioMetros: number
  /** Sello del servidor: la fecha de la declaración es parte de la prueba (D4). */
  declaradoEn: string
}

export type VeredictoUbicacion = 'DENTRO' | 'FUERA' | 'NO_CONCLUYENTE'

export interface UbicacionApunteGuardada {
  veredicto: VeredictoUbicacion
  /** null en FUERA por diseño: el PDF principal nunca imprime distancia de un FUERA. */
  distanciaMetros: number | null
  precisionMetros: number
  registradaEn: string
}

// --- Estado local de activación (espejo del "ya completó el alta", nunca
// sustituye al consentimiento acreditado en servidor) ---

/** true si el usuario ya completó la activación (consentimiento + permiso + centro). */
export function ubicacionActivada(): boolean {
  try {
    return globalThis.localStorage?.getItem(CLAVE_UBICACION_ACTIVA) === '1'
  } catch {
    return false
  }
}

export function marcaUbicacionActivada(): void {
  try {
    globalThis.localStorage?.setItem(CLAVE_UBICACION_ACTIVA, '1')
  } catch {
    // Sin almacenamiento no se recuerda la preferencia; no es fatal.
  }
}

export function marcaUbicacionDesactivada(): void {
  try {
    globalThis.localStorage?.removeItem(CLAVE_UBICACION_ACTIVA)
  } catch {
    // ídem
  }
}

/** true tras un 403 del servidor: el consentimiento dejó de estar vigente sin que el usuario lo pidiera aquí. */
export function avisoConsentimientoCaducado(): boolean {
  try {
    return globalThis.localStorage?.getItem(CLAVE_AVISO_CONSENTIMIENTO_CADUCADO) === '1'
  } catch {
    return false
  }
}

export function limpiaAvisoConsentimientoCaducado(): void {
  try {
    globalThis.localStorage?.removeItem(CLAVE_AVISO_CONSENTIMIENTO_CADUCADO)
  } catch {
    // ídem
  }
}

function marcaAvisoConsentimientoCaducado(): void {
  try {
    globalThis.localStorage?.setItem(CLAVE_AVISO_CONSENTIMIENTO_CADUCADO, '1')
  } catch {
    // ídem
  }
}

// --- HTTP ---

export const postConsentimientoUbicacion = () =>
  api.post<void>('/ubicacion/consentimiento', { versionTexto: VERSION_CONSENTIMIENTO_UBICACION })

/** Revoca (rellena revocadoEn en servidor); NO borra el histórico de ubicaciones ya guardado. */
export const deleteConsentimientoUbicacion = () => api.delete<void>('/ubicacion/consentimiento')

export const getCentroTrabajo = () => api.get<CentroTrabajoGuardado>('/centro-trabajo')

/** El radio lo pone el servidor: el cliente solo manda dónde está. Alias libre y opcional. */
export const putCentroTrabajo = (posicion: Posicion, alias?: string | null) =>
  api.put<CentroTrabajoGuardado>('/centro-trabajo', {
    latitud: posicion.latitud,
    longitud: posicion.longitud,
    alias: alias ?? null,
  })

/** Borra TODO el histórico de ubicaciones del usuario. El diario de fichajes queda intacto. */
export const deleteUbicaciones = () => api.delete<void>('/ubicaciones')

/**
 * Adjunta al apunte ya guardado la posición aproximada capturada AHORA.
 * `POST` (no `PUT`, corrección del contrato): crea un recurso inmutable, no
 * lo reemplaza.
 *
 * Se llama SIEMPRE fire-and-forget desde quien fichó, y solo después de que
 * el POST del fichaje ya se resolvió — nunca hay un await entre el toque del
 * usuario y ese POST. Esta función en sí nunca lanza hacia quien la llama:
 * cualquier fallo (sin fix, red, 409 ya existía, 422 fuera de ventana) se
 * pierde en silencio, porque el fichaje ya está guardado y es lo único que
 * importa (D1 del diseño). La única excepción que SÍ actúa es el 403: el
 * servidor dice que el consentimiento ya no está vigente (p. ej. revocado
 * desde otro dispositivo), así que aquí también se apaga la feature y se
 * dispara un aviso, en vez de seguir intentándolo en silencio para siempre.
 */
export async function anotaUbicacion(apunteId: string): Promise<void> {
  if (!ubicacionActivada()) {
    return
  }
  const posicion = await capturaPosicion()
  registraIntentoUbicacion(posicion !== null)
  if (posicion === null) {
    return
  }
  try {
    await api.post<UbicacionApunteGuardada>(`/fichajes/${apunteId}/ubicacion`, {
      latitud: posicion.latitud,
      longitud: posicion.longitud,
      precisionMetros: posicion.precisionMetros,
    })
  } catch (e) {
    if (e instanceof ApiError && e.status === 403) {
      marcaUbicacionDesactivada()
      marcaAvisoConsentimientoCaducado()
    }
  }
}
