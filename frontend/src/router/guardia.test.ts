import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { RouteLocationNormalized } from 'vue-router'
import { ApiError } from '../services/api'
import { CLAVE_SESION_PERSISTIDA } from '../lib/sesionPersistida'
import { useAuthStore } from '../stores/auth'
import { guardiaSesion } from './guardia'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
  postRefresh: vi.fn(),
  postLogout: vi.fn(),
  getMe: vi.fn(),
}))

import { getMe, postLogin, postRefresh } from '../services/auth'

function ruta(parcial: Partial<RouteLocationNormalized>): RouteLocationNormalized {
  return { meta: {}, fullPath: '/', name: undefined, ...parcial } as RouteLocationNormalized
}

/** El token es de solo lectura: la sesión de prueba se abre por la puerta de verdad. */
async function conSesion(): Promise<ReturnType<typeof useAuthStore>> {
  vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2099-01-01T00:00:00Z' })
  const auth = useAuthStore()
  await auth.iniciarSesion('ana@example.com', 'superclave123')
  return auth
}

/**
 * localStorage de mentira: en el entorno de test `globalThis.localStorage` es
 * undefined, así que sembramos el refresh persistido stubeándolo aquí.
 */
function stubStorage(inicial: Record<string, string> = {}): void {
  const datos = new Map<string, string>(Object.entries(inicial))
  vi.stubGlobal('localStorage', {
    getItem: (clave: string) => datos.get(clave) ?? null,
    setItem: (clave: string, valor: string) => void datos.set(clave, valor),
    removeItem: (clave: string) => void datos.delete(clave),
  })
}

function refreshPersistido(refreshToken: string, refreshExpiraEn: string): Record<string, string> {
  return {
    [CLAVE_SESION_PERSISTIDA]: JSON.stringify({
      refreshToken,
      refreshExpiraEn,
      familia: 'familia-test',
    }),
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('guardiaSesion', () => {
  it('deja pasar a rutas públicas sin sesión', async () => {
    expect(await guardiaSesion(ruta({ fullPath: '/perfil' }))).toBe(true)
  })

  it('manda a login (con redirect de vuelta) si la ruta exige sesión y no la hay', async () => {
    const resultado = await guardiaSesion(ruta({ meta: { requiereSesion: true }, fullPath: '/cuenta' }))

    expect(resultado).toEqual({ name: 'login', query: { redirect: '/cuenta' } })
  })

  it('deja pasar a rutas protegidas con sesión iniciada', async () => {
    await conSesion()

    expect(await guardiaSesion(ruta({ meta: { requiereSesion: true }, fullPath: '/cuenta' }))).toBe(true)
  })

  it('con sesión iniciada, login y registro redirigen a la cuenta', async () => {
    await conSesion()

    expect(await guardiaSesion(ruta({ name: 'login', fullPath: '/login' }))).toEqual({ name: 'cuenta' })
    expect(await guardiaSesion(ruta({ name: 'registro', fullPath: '/registro' }))).toEqual({
      name: 'cuenta',
    })
  })

  // --- Restauración de sesión al recargar (issue #220) ---

  it('con un refresh persistido válido, restaura la sesión y deja pasar a la ruta protegida', async () => {
    stubStorage(refreshPersistido('refresh-viejo', '2099-01-01T00:00:00Z'))
    vi.mocked(postRefresh).mockResolvedValue({ token: 'jwt-rotado', expiraEn: '2099-01-01T00:15:00Z', refreshToken: 'refresh-rotado', refreshExpiraEn: '2099-01-08T00:00:00Z' })
    vi.mocked(getMe).mockResolvedValue({ email: 'ana@example.com' })

    const resultado = await guardiaSesion(ruta({ meta: { requiereSesion: true }, fullPath: '/cuenta' }))

    expect(resultado).toBe(true)
    expect(postRefresh).toHaveBeenCalledWith('refresh-viejo')
  })

  it('con un refresh persistido que el servidor rechaza, manda a login con el redirect', async () => {
    stubStorage(refreshPersistido('refresh-malo', '2099-01-01T00:00:00Z'))
    vi.mocked(postRefresh).mockRejectedValue(new ApiError(401, 'API 401', null))

    const resultado = await guardiaSesion(ruta({ meta: { requiereSesion: true }, fullPath: '/cuenta' }))

    expect(resultado).toEqual({ name: 'login', query: { redirect: '/cuenta' } })
  })

  it('con sesión restaurable, ir a login redirige a la cuenta (sin flash de login)', async () => {
    stubStorage(refreshPersistido('refresh-viejo', '2099-01-01T00:00:00Z'))
    vi.mocked(postRefresh).mockResolvedValue({ token: 'jwt-rotado', expiraEn: '2099-01-01T00:15:00Z', refreshToken: 'refresh-rotado', refreshExpiraEn: '2099-01-08T00:00:00Z' })
    vi.mocked(getMe).mockResolvedValue({ email: 'ana@example.com' })

    const resultado = await guardiaSesion(ruta({ name: 'login', fullPath: '/login' }))

    expect(resultado).toEqual({ name: 'cuenta' })
  })
})
