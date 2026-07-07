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

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  let response: Response
  try {
    // Spread options first so caller headers merge with — not clobber — the defaults.
    response = await fetch(`${API_BASE}${path}`, {
      signal: controller.signal,
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    })
  } finally {
    clearTimeout(timeout)
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const message =
      (body && typeof body === 'object' && 'message' in body && String(body.message)) ||
      `API ${response.status}: ${response.statusText}`
    throw new ApiError(response.status, message, body)
  }

  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export const api = {
  get: <T>(path: string, options: RequestInit = {}) => request<T>(path, options),

  post: <T>(path: string, body: unknown, options: RequestInit = {}) =>
    request<T>(path, { ...options, method: 'POST', body: JSON.stringify(body) }),

  put: <T>(path: string, body: unknown, options: RequestInit = {}) =>
    request<T>(path, { ...options, method: 'PUT', body: JSON.stringify(body) }),

  delete: <T>(path: string, options: RequestInit = {}) =>
    request<T>(path, { ...options, method: 'DELETE' }),
}

export interface HealthResponse {
  status: string
}

export const getHealth = () => api.get<HealthResponse>('/health')
