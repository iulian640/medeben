/**
 * Base de "Anotar dónde fichas" (Capacitor): el único fichero que conoce el
 * plugin @capacitor/geolocation. Envoltorio fino y tipado, calcado en estilo
 * a notificaciones.ts.
 *
 * D2 del contrato de implementación: SOLO ubicación aproximada
 * (ACCESS_COARSE_LOCATION), nunca fina, nunca en segundo plano. Por eso todo
 * aquí usa el alias `coarseLocation` del plugin, nunca `location`.
 *
 * `capturaPosicion()` nunca lanza: "sin fix" es el caso normal (interiores),
 * no un error que mostrar.
 */
import { Geolocation, type PermissionState } from '@capacitor/geolocation'

/** Posición aproximada ya en el vocabulario del backend (D3: crudo, sin redondeos falsos). */
export interface Posicion {
  latitud: number
  longitud: number
  /** Precisión reportada por el sistema, en metros, redondeada a entero. */
  precisionMetros: number
}

/**
 * Estado del permiso, en el vocabulario que necesita la UI:
 * - `no_pedido`: nunca se ha preguntado.
 * - `denegado`: lo denegó una vez; Android todavía reabre el diálogo del sistema.
 * - `denegado_permanente`: Android ya no reabre el diálogo (dos denegaciones,
 *   o "no preguntar más"). Nunca se vuelve a insistir (regla de oro §6).
 * - `concedido`: hay permiso ahora mismo. No distingue por sí solo "para
 *   siempre" de "solo esta vez" — esa distinción la da, con el tiempo,
 *   `avisoPermisoCaducado()`.
 */
export type EstadoPermisoUbicacion = 'concedido' | 'no_pedido' | 'denegado' | 'denegado_permanente'

const TIMEOUT_MS = 6000
const MAXIMUM_AGE_MS = 120000

/** Por encima de esto un fix no dice nada útil: se descarta en el cliente, ni se manda. */
const PRECISION_MAXIMA_METROS = 5000

const CLAVE_INTENTOS_SIN_PERMISO = 'medeben.ubicacion.intentos-sin-permiso'

/** Fichajes seguidos con la feature activa pero sin fix antes de avisar (nunca al fichar, solo en Ajustes). */
const UMBRAL_AVISO_PERMISO = 3

function traduceEstado(estado: PermissionState): EstadoPermisoUbicacion {
  switch (estado) {
    case 'granted':
      return 'concedido'
    case 'prompt':
      return 'no_pedido'
    case 'prompt-with-rationale':
      return 'denegado'
    case 'denied':
    default:
      return 'denegado_permanente'
  }
}

/** Consulta el permiso actual sin pedirlo. Mira siempre el alias `coarseLocation` (D2). */
export async function permisoUbicacion(): Promise<EstadoPermisoUbicacion> {
  const estado = await Geolocation.checkPermissions()
  return traduceEstado(estado.coarseLocation)
}

/**
 * Pide el permiso al sistema. Pide EXCLUSIVAMENTE `coarseLocation`: nunca se
 * solicita `location` (que en Android arrastraría también ACCESS_FINE_LOCATION).
 */
export async function pideUbicacion(): Promise<EstadoPermisoUbicacion> {
  const estado = await Geolocation.requestPermissions({ permissions: ['coarseLocation'] })
  return traduceEstado(estado.coarseLocation)
}

/**
 * Captura la posición aproximada del momento. `enableHighAccuracy: false`
 * usa el provider de red (resuelve en interiores; el GPS puro no). Nunca
 * lanza: cualquier fallo (sin permiso, timeout, sin señal, plugin caído)
 * devuelve `null`, que el que llama trata como "no se adjunta nada" — jamás
 * como un error que mostrar.
 */
export async function capturaPosicion(): Promise<Posicion | null> {
  try {
    const posicion = await Geolocation.getCurrentPosition({
      enableHighAccuracy: false,
      timeout: TIMEOUT_MS,
      maximumAge: MAXIMUM_AGE_MS,
    })
    const precisionMetros = Math.round(posicion.coords.accuracy)
    if (precisionMetros > PRECISION_MAXIMA_METROS) {
      return null
    }
    return {
      latitud: posicion.coords.latitude,
      longitud: posicion.coords.longitude,
      precisionMetros,
    }
  } catch {
    return null
  }
}

/**
 * Registra si el último intento de anotar ubicación (con la feature activa)
 * tuvo un fix efectivo o no. Un `false` acumulado tres veces seguidas es la
 * señal de "el permiso de una sola vez ha caducado" (Android no lo dice
 * directamente: solo se infiere de que dejó de funcionar). Un `true`
 * reinicia la cuenta: un fallo suelto (interior, timeout puntual) no cuenta.
 */
export function registraIntentoUbicacion(huboFix: boolean): void {
  try {
    if (huboFix) {
      globalThis.localStorage?.removeItem(CLAVE_INTENTOS_SIN_PERMISO)
      return
    }
    const actual = Number(globalThis.localStorage?.getItem(CLAVE_INTENTOS_SIN_PERMISO) ?? '0')
    globalThis.localStorage?.setItem(CLAVE_INTENTOS_SIN_PERMISO, String(actual + 1))
  } catch {
    // Sin almacenamiento no hay contador que llevar: no es fatal, solo se
    // pierde el aviso discreto (nunca se convierte en un nag al fichar).
  }
}

/** true cuando toca mostrar en Ajustes el aviso discreto de permiso caducado. */
export function avisoPermisoCaducado(): boolean {
  try {
    const actual = Number(globalThis.localStorage?.getItem(CLAVE_INTENTOS_SIN_PERMISO) ?? '0')
    return actual >= UMBRAL_AVISO_PERMISO
  } catch {
    return false
  }
}
