/**
 * Candado de sesión ENTRE PESTAÑAS (issue #229), sobre la Web Locks API.
 *
 * Todas las pestañas del mismo origen compiten por un único candado con
 * nombre: mientras una rota el refresh token (o hace login/logout), las demás
 * esperan. Sin esta exclusión, dos pestañas pueden renovar a la vez con el
 * mismo token y el servidor lo toma por robo (revocaTodas → cae hasta la
 * sesión del móvil donde se ficha).
 *
 * Degradación DELIBERADA a best-effort (ejecutar sin candado) en dos casos:
 * - navigator.locks no existe: origen sin secure context (dev por http, el
 *   emulador de Android) o jsdom en tests. El bundle exige Chromium moderno,
 *   así que en producción (https) SIEMPRE hay locks; se avisa una vez por
 *   consola para que un no-op silencioso no pase desapercibido en un deploy
 *   mal servido.
 * - el candado no llega en ESPERA_MAX_CANDADO_MS: una pestaña colgada no puede
 *   dejar al usuario sin renovar sesión para siempre.
 *
 * OJO: el candado NO es reentrante. Nunca llames a conCandadoExclusivo desde
 * dentro de otra sección con candado (deadlock instantáneo con locks reales).
 */
export const NOMBRE_CANDADO_SESION = 'medeben.sesion'

/**
 * Tope de espera por el candado antes de degradar a best-effort. INVARIANTE
 * (review #229, HIGH): debe ser MAYOR que el timeout de la operación más larga
 * que se ejecuta bajo el candado — el POST de /auth/refresh con sus 30s
 * (TIMEOUT_REFRESH_MS). Si fuera menor, una pestaña en espera degradaría a
 * best-effort con un refresh legítimo aún en vuelo, vería su marcador enVuelo
 * y lo quemaría como si fuera huérfano. Hay un test que fija esta relación.
 */
export const ESPERA_MAX_CANDADO_MS = 40_000

let avisadoSinLocks = false

export async function conCandadoExclusivo<T>(fn: () => Promise<T> | T): Promise<T> {
  const locks = globalThis.navigator?.locks
  if (locks === undefined) {
    if (!avisadoSinLocks) {
      avisadoSinLocks = true
      console.warn(
        'Web Locks no disponible: la coordinación de sesión entre pestañas queda en best-effort. ' +
          'En producción esto delata un origen sin secure context (¿https?).',
      )
    }
    return fn()
  }

  const controlador = new AbortController()
  const temporizador = setTimeout(() => controlador.abort(), ESPERA_MAX_CANDADO_MS)
  try {
    return (await locks.request(NOMBRE_CANDADO_SESION, { signal: controlador.signal }, async () => {
      // Concedido: el temporizador ya no debe abortar la sección en marcha.
      clearTimeout(temporizador)
      return fn()
    })) as T
  } catch (e) {
    if (controlador.signal.aborted) {
      // Nadie soltó el candado a tiempo (¿pestaña colgada?): best-effort.
      console.warn('El candado de sesión no llegó a tiempo: se ejecuta sin exclusión.')
      return fn()
    }
    throw e
  } finally {
    clearTimeout(temporizador)
  }
}
