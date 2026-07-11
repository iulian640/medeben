/**
 * Persistencia del refresh token (issue #220). Decisión de producto: para que
 * el trabajador no tenga que hacer login cada vez que abre la app, la sesión
 * se restaura en silencio tras una recarga. Se persiste SOLO el refresh (B4):
 * rota en cada uso, es revocable y su reutilización se detecta en el servidor,
 * así que un token robado del storage se puede cortar. JAMÁS se guarda aquí el
 * access token ni el email — el resto de D38/RGPD ("ningún dato personal se
 * persiste en el navegador") sigue en pie; un refresh revocable no es el
 * diario del usuario.
 *
 * Acceso DEFENSIVO calcado de libreta.ts: si el navegador no ofrece
 * localStorage (modo privado estricto, WebView capada) o lanza al tocarlo,
 * nada de esto rompe — la sesión seguirá viva en memoria y morirá al recargar,
 * como antes del arreglo. Todo bajo UNA sola clave.
 */
export const CLAVE_SESION_PERSISTIDA = 'medeben.refresh'

export interface SesionPersistida {
  /** Refresh opaco (B4): rota en cada renovación. */
  refreshToken: string
  /** Instant ISO-8601 de caducidad del refresh, para descartarlo sin ir a la red. */
  refreshExpiraEn: string
}

export function guardarSesionPersistida(sesion: SesionPersistida): void {
  try {
    globalThis.localStorage?.setItem(CLAVE_SESION_PERSISTIDA, JSON.stringify(sesion))
  } catch {
    // Sin almacenamiento no se persiste: la sesión no sobrevivirá a la recarga.
  }
}

export function borrarSesionPersistida(): void {
  try {
    globalThis.localStorage?.removeItem(CLAVE_SESION_PERSISTIDA)
  } catch {
    // Sin almacenamiento no hay nada que borrar.
  }
}

/**
 * Lee lo persistido validando el shape antes de confiar en ello: si no hay
 * nada, el JSON está corrupto o el objeto no tiene los dos campos string, se
 * purga la clave y se devuelve null. Así una entrada manipulada (XSS, edición
 * manual del storage) no se cuela como sesión.
 */
export function leerSesionPersistida(): SesionPersistida | null {
  let crudo: string | null
  try {
    crudo = globalThis.localStorage?.getItem(CLAVE_SESION_PERSISTIDA) ?? null
  } catch {
    return null
  }
  if (crudo === null) {
    return null
  }
  try {
    const dato: unknown = JSON.parse(crudo)
    if (esSesionPersistida(dato)) {
      return dato
    }
  } catch {
    // JSON corrupto: cae a la purga de abajo, como cualquier otra basura.
  }
  borrarSesionPersistida()
  return null
}

function esSesionPersistida(dato: unknown): dato is SesionPersistida {
  if (typeof dato !== 'object' || dato === null) {
    return false
  }
  const candidato = dato as Record<string, unknown>
  return (
    typeof candidato.refreshToken === 'string' && typeof candidato.refreshExpiraEn === 'string'
  )
}
