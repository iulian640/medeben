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
  /** Instant ISO-8601 de caducidad del token. */
  expiraEn: string
}

/** Límites del backend (RegistroRequest): mín. 10 por política, máx. 72 por BCrypt. */
export const PASSWORD_MIN = 10
export const PASSWORD_MAX = 72

export const postRegistro = (email: string, password: string) =>
  api.post<Usuario>('/auth/registro', { email, password })

export const postLogin = (email: string, password: string) =>
  api.post<TokenEmitido>('/auth/login', { email, password })

export const getMe = () => api.get<Usuario>('/me')
