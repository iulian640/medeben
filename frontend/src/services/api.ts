/**
 * Minimal API client over fetch.
 *
 * In dev, Vite proxies /api to the local Spring Boot backend (localhost:8080).
 * In production (same-origin web) the relative default works as-is; once the app
 * is packaged with Capacitor the WebView is a different origin, so the base URL
 * comes from VITE_API_URL when present.
 */
const API_BASE = `${import.meta.env.VITE_API_URL ?? ''}/api/v1`

const REQUEST_TIMEOUT_MS = 15000

export class ApiError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, message: string, body: unknown = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

/**
 * Access token SOLO en memoria (requisito de seguridad: nunca localStorage,
 * sessionStorage ni cookies legibles por JS — un XSS no debe poder exfiltrar
 * el credencial que viaja en cada petición). Al recargar la página se pierde,
 * pero la sesión se restaura en silencio con el refresh persistido, sin volver
 * a pedir login (issue #220, en stores/auth.ts + lib/sesionPersistida.ts).
 */
let authToken: string | null = null

/** Aviso de sesión inválida (401 con token). Lo registra main.ts para limpiar sesión y llevar a login. */
let onUnauthorized: (() => void) | null = null

/**
 * Renovación de la sesión (B4). Lo registra main.ts: devuelve true si el
 * refresh rotó los tokens (y la petición original puede reintentarse) o false
 * si la sesión ya no tiene arreglo (toca expulsar).
 */
let onRefresh: (() => Promise<boolean>) | null = null

/** Single-flight: N peticiones con 401 a la vez comparten UN solo refresh. */
let refreshEnVuelo: Promise<boolean> | null = null

/**
 * Token de la sesión a la que pertenece el refresh en curso (el que estaba en
 * el módulo cuando arrancó). Va SIEMPRE de la mano de refreshEnVuelo (ambos se
 * ponen a null en el mismo finally). Sirve para que solo las peticiones de ESA
 * misma sesión se cuelguen del refresh y se reintenten con el token rotado: en
 * un dispositivo compartido, el 401 de una sesión ajena no puede reintentarse
 * con el token de otra (bug B3).
 */
let tokenDelRefresco: string | null = null

/** Opciones del cliente además de las de fetch. */
export interface OpcionesApi extends RequestInit {
  /**
   * No adjuntar el Bearer aunque haya sesión. Para /auth/refresh y
   * /auth/logout: viajan con el refresh en el body, y un access CADUCADO en
   * la cabecera haría que el resource server respondiera 401 antes de mirar
   * nada (y de paso evita cualquier bucle refresh→401→refresh).
   */
  anonimo?: boolean
  /**
   * Plazo propio en ms (por defecto REQUEST_TIMEOUT_MS). Para /auth/refresh
   * (issue #229): un refresh abortado por timeout deja el token en estado
   * desconocido y fuerza un re-login, así que en red móvil floja conviene
   * aguantarle más que a una petición corriente.
   */
  timeoutMs?: number
}

export function setAuthToken(token: string | null) {
  authToken = token
}

export function setOnUnauthorized(handler: (() => void) | null) {
  onUnauthorized = handler
}

/**
 * Registra el renovador. Resetea también el guardián single-flight A PROPÓSITO:
 * esta función solo se llama en el arranque (main.ts) y en los tests, y un
 * handler nuevo no debe heredar una promesa del handler anterior. Si algún día
 * se reasignara en caliente, separar ambas cosas.
 */
export function setOnRefresh(handler: (() => Promise<boolean>) | null) {
  onRefresh = handler
  refreshEnVuelo = null
  tokenDelRefresco = null
}

async function envia(path: string, options: OpcionesApi, esReintento = false): Promise<Response> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? REQUEST_TIMEOUT_MS)

  // Capturado antes del await: si la sesión cambia en vuelo, el 401 de esta
  // respuesta solo dispara el handler si ESTA petición iba autenticada.
  const tokenEnviado = options.anonimo ? null : authToken
  // fetch no conoce 'anonimo' ni 'timeoutMs': fuera antes de pasárselo.
  const { anonimo: _anonimo, timeoutMs: _timeoutMs, ...init } = options
  void _anonimo
  void _timeoutMs

  let response: Response
  try {
    // Spread options first so caller headers merge with — not clobber — the defaults.
    response = await fetch(`${API_BASE}${path}`, {
      signal: controller.signal,
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(tokenEnviado ? { Authorization: `Bearer ${tokenEnviado}` } : {}),
        ...init.headers,
      },
    })
  } finally {
    clearTimeout(timeout)
  }

  // 401 con token: antes de expulsar se intenta renovar la sesión UNA vez
  // (B4). Sin token (login fallido, refresh, logout) no hay nada que renovar.
  if (response.status === 401 && tokenEnviado !== null) {
    if (!esReintento && onRefresh !== null) {
      // Un refresh SOLO lo arranca la sesión que emitió ESTA petición y que
      // SIGUE siendo la del módulo. En un dispositivo compartido (tablet de
      // barra en hostelería) la sesión puede cambiar con la petición en vuelo:
      // Bea inicia sesión mientras el POST de Ana —con el access ya caducado—
      // sigue viajando. Sin esta guarda, el 401 de Ana dispararía el refresh de
      // la sesión de BEA. Mismo patrón que auth.ts (borrarCuenta/tokenAlEmpezar):
      // el token se captura antes del await (tokenEnviado) y se comprueba
      // después. tokenDelRefresco recuerda a qué sesión pertenece el refresh en
      // curso, para el reintento de abajo.
      if (refreshEnVuelo === null && authToken === tokenEnviado) {
        tokenDelRefresco = tokenEnviado
        refreshEnVuelo = onRefresh().finally(() => {
          refreshEnVuelo = null
          tokenDelRefresco = null
        })
      }
      // Reintento SOLO si el refresh en curso es el de la sesión que emitió
      // ESTA petición (mismo token de arranque). Así el single-flight sigue
      // funcionando —varias peticiones de la MISMA sesión comparten un refresh
      // y reintentan con el token ya rotado—, pero la petición de una sesión
      // ajena no se cuela en el refresh de otra ni se reintenta con su token:
      // cae al throw de más abajo (bug B3). No basta con authToken ===
      // tokenEnviado aquí: un refresh legítimo de la MISMA sesión rota el token,
      // y entonces authToken ya no coincide con tokenEnviado (rompería el
      // single-flight de la segunda petición).
      if (refreshEnVuelo !== null && tokenEnviado === tokenDelRefresco) {
        const renovado = await refreshEnVuelo
        if (renovado) {
          // Reintento único con el token ya rotado (envia lo relee del módulo).
          return envia(path, options, true)
        }
      }
    }
    // Solo se expulsa si la sesión actual sigue siendo la que emitió ESTA
    // petición (vue review, CRITICAL): en un dispositivo compartido, el 401
    // tardío de una sesión ya sustituida (Ana salió, Bea entró mientras el
    // refresh viajaba) no puede echar a la persona que está dentro ahora.
    if (authToken === tokenEnviado) {
      onUnauthorized?.()
    }
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const message =
      (body && typeof body === 'object' && 'message' in body && String(body.message)) ||
      `API ${response.status}: ${response.statusText}`
    throw new ApiError(response.status, message, body)
  }

  return response
}

async function request<T>(path: string, options: OpcionesApi = {}): Promise<T> {
  const response = await envia(path, options)

  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T
  }

  return response.json() as Promise<T>
}

/**
 * GET binario (el informe PDF): mismas reglas que el resto — timeout, token en
 * memoria, refresh+reintento del 401 y errores RFC 7807 — pero devolviendo el Blob.
 */
async function requestBlob(path: string, options: OpcionesApi = {}): Promise<Blob> {
  const response = await envia(path, options)
  return response.blob()
}

export const api = {
  get: <T>(path: string, options: OpcionesApi = {}) => request<T>(path, options),

  getBlob: (path: string, options: OpcionesApi = {}) => requestBlob(path, options),

  post: <T>(path: string, body: unknown, options: OpcionesApi = {}) =>
    request<T>(path, { ...options, method: 'POST', body: JSON.stringify(body) }),

  put: <T>(path: string, body: unknown, options: OpcionesApi = {}) =>
    request<T>(path, { ...options, method: 'PUT', body: JSON.stringify(body) }),

  // El body es opcional: el borrado de cuenta re-confirma con la contraseña.
  delete: <T>(path: string, body?: unknown, options: OpcionesApi = {}) =>
    request<T>(path, {
      ...options,
      method: 'DELETE',
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    }),
}

export interface HealthResponse {
  status: string
}

export const getHealth = () => api.get<HealthResponse>('/health')
