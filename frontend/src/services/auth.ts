/**
 * Endpoints de cuenta (registro, login, quién soy). Tipos calcados de los DTOs
 * del backend (RegistroRequest, TokenResponse, UsuarioResponse).
 */
import { api } from './api'

export interface Usuario {
  email: string
}

export interface TokenEmitido {
  token: string
  /** Instant ISO-8601 de caducidad del access token (corto). */
  expiraEn: string
  /** Refresh opaco (B4): rota en cada uso y se puede revocar en el servidor. */
  refreshToken: string
  refreshExpiraEn: string
}

/** Límites del backend (RegistroRequest): mín. 10 por política, máx. 72 por BCrypt. */
export const PASSWORD_MIN = 10
export const PASSWORD_MAX = 72

export const postRegistro = (email: string, password: string) =>
  api.post<Usuario>('/auth/registro', { email, password })

export const postLogin = (email: string, password: string) =>
  api.post<TokenEmitido>('/auth/login', { email, password })

export const getMe = () => api.get<Usuario>('/me')

/** Borrado de cuenta (RGPD art. 17): destruye TODOS los datos; re-confirma con la contraseña. */
export const deleteCuenta = (password: string) => api.delete<void>('/cuenta', { password })

/** Plazo extra para el POST de renovación (issue #229): un refresh abortado por
 * timeout deja el token en estado desconocido → desarme y re-login. En red
 * móvil floja, mejor esperar el doble que expulsar al usuario por impaciencia. */
export const TIMEOUT_REFRESH_MS = 30_000

/* refresh y logout van SIN Bearer (anonimo): el access puede estar caducado y
 * un 401 del resource server aquí montaría un bucle. El refresh viaja en el body. */
export const postRefresh = (refreshToken: string) =>
  api.post<TokenEmitido>('/auth/refresh', { refreshToken }, { anonimo: true, timeoutMs: TIMEOUT_REFRESH_MS })

export const postLogout = (refreshToken: string) =>
  api.post<void>('/auth/logout', { refreshToken }, { anonimo: true })
