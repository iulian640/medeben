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
 * Coordinación entre pestañas (issue #229): la clave es única y compartida.
 * - `familia` identifica la CADENA de rotaciones (un UUID aleatorio generado
 *   en el login, que la rotación conserva). No identifica al usuario, no viaja
 *   nunca al servidor: solo evita que una pestaña adopte el token de una
 *   sesión ajena (otro login pisó el slot) y mezcle cuentas.
 * - `enVuelo` es el marcador del protocolo de token quemado: se persiste justo
 *   antes del POST de renovación. Si alguien encuentra el marcador puesto, ese
 *   refresh pudo gastarse sin que la respuesta llegara (pestaña muerta en
 *   vuelo): NO debe reutilizarse — se revoca (revocado da un 401 plano; usado
 *   dos veces dispara la alarma antirrobo y revoca TODAS las sesiones).
 *
 * Acceso DEFENSIVO calcado de libreta.ts: si el navegador no ofrece
 * localStorage (modo privado estricto, WebView capada) o lanza al tocarlo,
 * nada de esto rompe — la sesión seguirá viva en memoria y morirá al recargar,
 * como antes del arreglo. Todo bajo UNA sola clave.
 */
export const CLAVE_SESION_PERSISTIDA = 'medeben.refresh'

/** Clave DEDICADA de la sonda de almacenamiento: jamás la de la sesión real. */
export const CLAVE_SONDA_ALMACENAMIENTO = 'medeben.probe'

export interface SesionPersistida {
  /** Refresh opaco (B4): rota en cada renovación. */
  refreshToken: string
  /** Instant ISO-8601 de caducidad del refresh, para descartarlo sin ir a la red. */
  refreshExpiraEn: string
  /** Cadena de rotaciones a la que pertenece el token (issue #229). */
  familia: string
  /** Marcador de renovación en vuelo (protocolo de token quemado, issue #229). */
  enVuelo?: string
}

/**
 * Persiste la sesión. Devuelve false si el storage no está o falla (cuota,
 * modo privado): el protocolo de token quemado necesita saber si el marcador
 * llegó a escribirse de verdad antes de arriesgar el POST.
 */
export function guardarSesionPersistida(sesion: SesionPersistida): boolean {
  try {
    const almacen = globalThis.localStorage
    if (almacen === undefined) {
      return false
    }
    almacen.setItem(CLAVE_SESION_PERSISTIDA, JSON.stringify(sesion))
    return true
  } catch {
    // Sin almacenamiento no se persiste: la sesión no sobrevivirá a la recarga.
    return false
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
 * Borrado CONDICIONAL (compare-and-delete) para coordinar pestañas (issue #220).
 * La clave es única y compartida por todas las pestañas del mismo origen, pero
 * cada pestaña tiene su propio refresh en memoria. Cuando la limpieza la dispara
 * un refresh RECHAZADO (restauración fallida o expulsión por 401), otra pestaña
 * pudo haber rotado el token bajo la misma clave: en ese caso el refresh
 * persistido ya NO coincide con el que esta pestaña intentó usar, y purgarlo
 * borraría el refresh vigente de la otra pestaña (que sigue autenticada). Por
 * eso solo se purga si lo persistido sigue siendo el `refreshEsperado`, o si ya
 * no hay nada válido. Las salidas deliberadas (logout, borrado de cuenta) siguen
 * usando borrarSesionPersistida() sin condición.
 */
export function borrarSesionPersistidaSi(refreshEsperado: string): void {
  const actual = leerSesionPersistida()
  if (actual !== null && actual.refreshToken !== refreshEsperado) {
    // Otra pestaña ya rotó el token: se respeta su refresh vigente.
    return
  }
  borrarSesionPersistida()
}

/**
 * Borrado condicional por FAMILIA (issue #229): purga solo si el slot sigue
 * ocupado por la cadena esperada. Da igual cuántas rotaciones haya hecho otra
 * pestaña de la MISMA sesión (el token cambia, la familia no); lo que protege
 * es el slot de una sesión AJENA que haya hecho login después.
 */
export function borrarSesionPersistidaSiFamilia(familiaEsperada: string): void {
  const actual = leerSesionPersistida()
  if (actual !== null && actual.familia !== familiaEsperada) {
    return
  }
  borrarSesionPersistida()
}

/**
 * Lee lo persistido validando el shape antes de confiar en ello: si no hay
 * nada, el JSON está corrupto o faltan campos (incluidos los blobs legacy de
 * antes del campo familia), se purga la clave y se devuelve null. Así una
 * entrada manipulada (XSS, edición manual del storage) no se cuela como
 * sesión. La purga es compare-and-delete sobre el valor crudo: esta lectura
 * puede ocurrir fuera del candado, y purgar a ciegas podría borrar el blob
 * que otra pestaña acaba de escribir.
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
  try {
    if (globalThis.localStorage?.getItem(CLAVE_SESION_PERSISTIDA) === crudo) {
      borrarSesionPersistida()
    }
  } catch {
    // Si el storage falla aquí, tampoco había nada rescatable.
  }
  return null
}

/**
 * ¿El almacenamiento compartido FUNCIONA de verdad? Distingue "la clave no
 * está porque otra pestaña cerró la sesión" (storage sano → hay que acatarlo)
 * de "la clave no está porque no hay storage" (modo privado → la sesión vive
 * solo en la memoria de esta pestaña y no hay nada que coordinar).
 */
export function almacenamientoFunciona(): boolean {
  try {
    const almacen = globalThis.localStorage
    if (almacen === undefined) {
      return false
    }
    almacen.setItem(CLAVE_SONDA_ALMACENAMIENTO, '1')
    almacen.removeItem(CLAVE_SONDA_ALMACENAMIENTO)
    return true
  } catch {
    return false
  }
}

/**
 * Identificador aleatorio de cadena (familia). NO es un credencial ni un dato
 * personal: solo distingue "mi sesión rotada" de "otra sesión pisó el slot".
 * crypto.randomUUID solo existe en secure contexts (en dev por http — el
 * emulador de Android — no está), así que se degrada a getRandomValues y, en
 * último extremo, a Math.random: para un discriminador basta.
 */
export function generarFamilia(): string {
  const cripto = globalThis.crypto
  if (cripto?.randomUUID !== undefined) {
    return cripto.randomUUID()
  }
  if (cripto?.getRandomValues !== undefined) {
    const bytes = cripto.getRandomValues(new Uint8Array(16))
    return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}

function esSesionPersistida(dato: unknown): dato is SesionPersistida {
  if (typeof dato !== 'object' || dato === null) {
    return false
  }
  const candidato = dato as Record<string, unknown>
  return (
    typeof candidato.refreshToken === 'string' &&
    typeof candidato.refreshExpiraEn === 'string' &&
    typeof candidato.familia === 'string' &&
    (candidato.enVuelo === undefined || typeof candidato.enVuelo === 'string')
  )
}
