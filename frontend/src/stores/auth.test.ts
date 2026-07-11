import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '../services/api'
import { CLAVE_SESION_PERSISTIDA, leerSesionPersistida } from '../lib/sesionPersistida'
import { useAuthStore } from './auth'
import { useCuentaStore } from './cuenta'
import { usePerfilStore } from './perfil'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
  deleteCuenta: vi.fn(),
  postRefresh: vi.fn(),
  postLogout: vi.fn(),
  getMe: vi.fn(),
}))
vi.mock('../services/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/api')>()),
  setAuthToken: vi.fn(),
}))

import { deleteCuenta, getMe, postLogin, postLogout, postRefresh, postRegistro } from '../services/auth'
import { setAuthToken } from '../services/api'

/**
 * localStorage de mentira respaldado por un Map: en el entorno de test
 * `globalThis.localStorage` es undefined, así que la persistencia del refresh
 * (issue #220) solo es observable si lo stubbeamos aquí.
 */
function stubStorage(inicial: Record<string, string> = {}): Map<string, string> {
  const datos = new Map<string, string>(Object.entries(inicial))
  vi.stubGlobal('localStorage', {
    getItem: (clave: string) => datos.get(clave) ?? null,
    setItem: (clave: string, valor: string) => void datos.set(clave, valor),
    removeItem: (clave: string) => void datos.delete(clave),
  })
  return datos
}

/** Siembra un refresh ya persistido bajo la clave real, para probar la restauración. */
function refreshPersistido(refreshToken: string, refreshExpiraEn: string): Record<string, string> {
  return { [CLAVE_SESION_PERSISTIDA]: JSON.stringify({ refreshToken, refreshExpiraEn }) }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  // El logout remoto es fire-and-forget: por defecto resuelve, y los tests
  // que quieren red rota lo re-stubbean.
  vi.mocked(postLogout).mockResolvedValue(undefined)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('auth store', () => {
  it('login OK: guarda token y email en memoria y lo registra en el cliente API', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()

    const ok = await auth.iniciarSesion('ana@example.com', 'superclave123')

    expect(ok).toBe(true)
    expect(auth.autenticado).toBe(true)
    expect(auth.email).toBe('ana@example.com')
    expect(setAuthToken).toHaveBeenCalledWith('jwt-123')
    expect(auth.error).toBeNull()
  })

  it('login KO: expone el detail RFC 7807 y no deja sesión a medias', async () => {
    vi.mocked(postLogin).mockRejectedValue(
      new ApiError(401, 'API 401', { status: 401, detail: 'Email o contraseña incorrectos' }),
    )
    const auth = useAuthStore()

    const ok = await auth.iniciarSesion('ana@example.com', 'mala')

    expect(ok).toBe(false)
    expect(auth.autenticado).toBe(false)
    expect(auth.email).toBeNull()
    expect(auth.error).toBe('Email o contraseña incorrectos')
    expect(setAuthToken).not.toHaveBeenCalledWith(expect.stringContaining('jwt'))
  })

  it('registro OK encadena el login con las mismas credenciales', async () => {
    vi.mocked(postRegistro).mockResolvedValue({ email: 'ana@example.com' })
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-456', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-456', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()

    const ok = await auth.registrarse('ana@example.com', 'superclave123')

    expect(ok).toBe(true)
    expect(postRegistro).toHaveBeenCalledWith('ana@example.com', 'superclave123')
    expect(postLogin).toHaveBeenCalledWith('ana@example.com', 'superclave123')
    expect(auth.autenticado).toBe(true)
  })

  it('registro KO (409 email ya registrado): error legible y sin login', async () => {
    vi.mocked(postRegistro).mockRejectedValue(
      new ApiError(409, 'API 409', { status: 409, detail: 'Ese email ya está registrado' }),
    )
    const auth = useAuthStore()

    const ok = await auth.registrarse('ana@example.com', 'superclave123')

    expect(ok).toBe(false)
    expect(auth.error).toBe('Ese email ya está registrado')
    expect(postLogin).not.toHaveBeenCalled()
  })

  it('cerrarSesion limpia todo y desregistra el token del cliente API', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    auth.cerrarSesion()

    expect(auth.autenticado).toBe(false)
    expect(auth.email).toBeNull()
    expect(setAuthToken).toHaveBeenLastCalledWith(null)
  })

  it('sesionCaducada limpia la sesión y deja un aviso para la pantalla de login', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    auth.sesionCaducada()

    expect(auth.autenticado).toBe(false)
    expect(setAuthToken).toHaveBeenLastCalledWith(null)
    expect(auth.aviso).toMatch(/sesión/i)
  })

  it('cerrarSesion vacía también el store de cuenta (dispositivo compartido)', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const cuenta = useCuentaStore()
    cuenta.provincia = 'Madrid'
    cuenta.subsector = 'hosteleria'
    cuenta.puestoId = 'cocinero'
    cuenta.salarioBaseMensual = 1500
    cuenta.plusesAnuales = 600
    cuenta.convenioId = 'madrid-hosteleria'

    auth.cerrarSesion()

    expect(cuenta.provincia).toBeNull()
    expect(cuenta.subsector).toBeNull()
    expect(cuenta.puestoId).toBeNull()
    expect(cuenta.salarioBaseMensual).toBeNull()
    expect(cuenta.plusesAnuales).toBeNull()
    expect(cuenta.convenioId).toBeNull()
  })

  it('cerrarSesion vacía también el store de perfil (dispositivo compartido, review de seguridad)', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const perfil = usePerfilStore()
    perfil.provincia = 'Madrid'
    perfil.subsector = 'hosteleria'
    perfil.puestoId = 'cocinero'

    auth.cerrarSesion()

    expect(perfil.provincia).toBeNull()
    expect(perfil.subsector).toBeNull()
    expect(perfil.puestoId).toBeNull()
  })

  it('la expulsión por 401 (sesionCaducada) también vacía el store de cuenta', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const cuenta = useCuentaStore()
    cuenta.provincia = 'Madrid'
    cuenta.salarioBaseMensual = 1500

    auth.sesionCaducada()

    expect(cuenta.provincia).toBeNull()
    expect(cuenta.salarioBaseMensual).toBeNull()
  })

  it('borrarCuenta OK: borra en el servidor, limpia la sesión ENTERA y deja el aviso de despedida', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    vi.mocked(deleteCuenta).mockResolvedValue(undefined)
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const cuenta = useCuentaStore()
    cuenta.provincia = 'Madrid'
    cuenta.salarioBaseMensual = 1500

    const ok = await auth.borrarCuenta('superclave123')

    expect(ok).toBe(true)
    expect(deleteCuenta).toHaveBeenCalledWith('superclave123')
    expect(auth.autenticado).toBe(false)
    expect(setAuthToken).toHaveBeenLastCalledWith(null)
    // Dispositivo compartido: nada del usuario borrado queda en memoria.
    expect(cuenta.provincia).toBeNull()
    expect(cuenta.salarioBaseMensual).toBeNull()
    expect(auth.aviso).toMatch(/borrado/i)
  })

  it('borrarCuenta con contraseña incorrecta (403): error legible y la sesión NO se toca', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    vi.mocked(deleteCuenta).mockRejectedValue(
      new ApiError(403, 'API 403', { status: 403, detail: 'La contraseña no es correcta' }),
    )
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const ok = await auth.borrarCuenta('laMala1234')

    expect(ok).toBe(false)
    expect(auth.errorBorrado).toBe('La contraseña no es correcta')
    expect(auth.autenticado).toBe(true)
    expect(auth.email).toBe('ana@example.com')
  })

  it('CRITICAL review: un borrado que resuelve tarde NO pisa la sesión de OTRO usuario', async () => {
    // Dispositivo compartido: Ana lanza el borrado, cierra sesión antes de que
    // resuelva, y Bea inicia sesión. La promesa vieja no puede limpiar la
    // sesión de Bea ni dejarle el aviso de despedida de Ana.
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-ana', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-ana', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    let resolverBorrado: () => void = () => {}
    vi.mocked(deleteCuenta).mockReturnValue(
      new Promise<void>((resolve) => {
        resolverBorrado = () => resolve()
      }),
    )
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const enVuelo = auth.borrarCuenta('superclave123')
    // Mientras el DELETE viaja: Ana sale y entra Bea.
    auth.cerrarSesion()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-bea', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-bea', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    await auth.iniciarSesion('bea@example.com', 'otraclave123')

    resolverBorrado()
    await enVuelo

    // La sesión de Bea sigue intacta y sin el aviso de borrado de Ana.
    expect(auth.autenticado).toBe(true)
    expect(auth.email).toBe('bea@example.com')
    expect(setAuthToken).toHaveBeenLastCalledWith('jwt-bea')
    expect(auth.aviso).toBeNull()
  })

  it('un intento nuevo de borrado limpia el error del intento anterior', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    vi.mocked(deleteCuenta)
      .mockRejectedValueOnce(
        new ApiError(403, 'API 403', { status: 403, detail: 'La contraseña no es correcta' }),
      )
      .mockResolvedValueOnce(undefined)
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    await auth.borrarCuenta('laMala1234')
    const ok = await auth.borrarCuenta('superclave123')

    expect(ok).toBe(true)
    expect(auth.errorBorrado).toBeNull()
  })

  // --- Refresh y logout real (B4) ---

  it('refrescar rota los DOS tokens y sincroniza el cliente API', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    vi.mocked(postRefresh).mockResolvedValue({
      token: 'jwt-rotado',
      expiraEn: '2026-07-09T00:15:00Z',
      refreshToken: 'refresh-rotado',
      refreshExpiraEn: '2026-07-17T00:15:00Z',
    })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const ok = await auth.refrescar()

    expect(ok).toBe(true)
    expect(postRefresh).toHaveBeenCalledWith('refresh-jwt-123')
    expect(auth.token).toBe('jwt-rotado')
    expect(auth.refreshToken).toBe('refresh-rotado')
    expect(setAuthToken).toHaveBeenLastCalledWith('jwt-rotado')
  })

  it('refrescar sin sesión → false, sin llamar a la red', async () => {
    const auth = useAuthStore()

    expect(await auth.refrescar()).toBe(false)
    expect(postRefresh).not.toHaveBeenCalled()
  })

  it('refrescar con el refresh rechazado (revocado/caducado) → false y la sesión local queda como estaba', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    vi.mocked(postRefresh).mockRejectedValue(new ApiError(401, 'API 401', null))
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    expect(await auth.refrescar()).toBe(false)
    // La expulsión la decide el cliente API, no este método.
    expect(auth.token).toBe('jwt-123')
  })

  it('un refresh que resuelve tarde NO resucita una sesión ya cerrada (y revoca el token nuevo)', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    let resolverRefresh: () => void = () => {}
    vi.mocked(postRefresh).mockReturnValue(
      new Promise((resolve) => {
        resolverRefresh = () =>
          resolve({
            token: 'jwt-zombi',
            expiraEn: '2026-07-09T00:15:00Z',
            refreshToken: 'refresh-zombi',
            refreshExpiraEn: '2026-07-17T00:15:00Z',
          })
      }),
    )
    vi.mocked(postLogout).mockResolvedValue(undefined)
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const enVuelo = auth.refrescar()
    auth.cerrarSesion()
    resolverRefresh()

    expect(await enVuelo).toBe(false)
    expect(auth.autenticado).toBe(false)
    expect(setAuthToken).toHaveBeenLastCalledWith(null)
    // El refresh rotado que nadie va a usar se revoca para no dejarlo vivo.
    expect(postLogout).toHaveBeenCalledWith('refresh-zombi')
  })

  it('cerrarSesion revoca el refresh en el servidor (logout real) y limpia aunque la red falle', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    vi.mocked(postLogout).mockRejectedValue(new ApiError(0, 'sin red', null))
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    auth.cerrarSesion()

    expect(postLogout).toHaveBeenCalledWith('refresh-jwt-123')
    expect(auth.autenticado).toBe(false)
  })

  it('un login nuevo limpia el aviso de sesión caducada anterior', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()
    auth.sesionCaducada()

    await auth.iniciarSesion('ana@example.com', 'superclave123')

    expect(auth.aviso).toBeNull()
  })

  // --- Sesión persistente: sobrevivir a la recarga (issue #220) ---

  it('iniciarSesion persiste SOLO el refresh y su caducidad, ni el access ni el email', async () => {
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()

    await auth.iniciarSesion('ana@example.com', 'superclave123')

    expect(leerSesionPersistida()).toEqual({
      refreshToken: 'refresh-jwt-123',
      refreshExpiraEn: '2099-01-01T00:00:00Z',
    })
    // El email nunca toca el disco (D38): lo persistido no lo contiene.
    expect(datos.get(CLAVE_SESION_PERSISTIDA)).not.toContain('ana@example.com')
  })

  it('restaurarSesion con refresh válido rota los tokens, recupera el email y re-persiste el nuevo refresh', async () => {
    stubStorage(refreshPersistido('refresh-viejo', '2099-01-01T00:00:00Z'))
    vi.mocked(postRefresh).mockResolvedValue({
      token: 'jwt-rotado',
      expiraEn: '2099-01-01T00:15:00Z',
      refreshToken: 'refresh-rotado',
      refreshExpiraEn: '2099-01-08T00:00:00Z',
    })
    vi.mocked(getMe).mockResolvedValue({ email: 'ana@example.com' })
    const auth = useAuthStore()

    const ok = await auth.restaurarSesion()

    expect(ok).toBe(true)
    expect(auth.autenticado).toBe(true)
    expect(postRefresh).toHaveBeenCalledWith('refresh-viejo')
    expect(auth.token).toBe('jwt-rotado')
    expect(auth.email).toBe('ana@example.com')
    // El refresh rotado queda persistido, listo para la siguiente recarga.
    expect(leerSesionPersistida()).toEqual({
      refreshToken: 'refresh-rotado',
      refreshExpiraEn: '2099-01-08T00:00:00Z',
    })
  })

  it('restaurarSesion con el refresh rechazado limpia memoria Y storage', async () => {
    const datos = stubStorage(refreshPersistido('refresh-malo', '2099-01-01T00:00:00Z'))
    vi.mocked(postRefresh).mockRejectedValue(new ApiError(401, 'API 401', null))
    const auth = useAuthStore()

    const ok = await auth.restaurarSesion()

    expect(ok).toBe(false)
    expect(auth.autenticado).toBe(false)
    expect(setAuthToken).toHaveBeenLastCalledWith(null)
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('restaurarSesion con el refresh ya caducado → false SIN ir a la red, y purga el storage', async () => {
    const datos = stubStorage(refreshPersistido('refresh-caduco', '2000-01-01T00:00:00Z'))
    const auth = useAuthStore()

    const ok = await auth.restaurarSesion()

    expect(ok).toBe(false)
    expect(postRefresh).not.toHaveBeenCalled()
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('restaurarSesion con una caducidad ilegible → false SIN ir a la red, y purga el storage', async () => {
    const datos = stubStorage(refreshPersistido('refresh-x', 'no-es-una-fecha'))
    const auth = useAuthStore()

    const ok = await auth.restaurarSesion()

    expect(ok).toBe(false)
    expect(postRefresh).not.toHaveBeenCalled()
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('restaurarSesion sigue siendo válida si getMe falla por red: true con email null (no expulsa)', async () => {
    stubStorage(refreshPersistido('refresh-viejo', '2099-01-01T00:00:00Z'))
    vi.mocked(postRefresh).mockResolvedValue({
      token: 'jwt-rotado',
      expiraEn: '2099-01-01T00:15:00Z',
      refreshToken: 'refresh-rotado',
      refreshExpiraEn: '2099-01-08T00:00:00Z',
    })
    vi.mocked(getMe).mockRejectedValue(new ApiError(0, 'sin red', null))
    const auth = useAuthStore()

    const ok = await auth.restaurarSesion()

    expect(ok).toBe(true)
    expect(auth.autenticado).toBe(true)
    expect(auth.email).toBeNull()
  })

  it('restaurarSesion sin nada persistido → false sin llamar a la red', async () => {
    stubStorage()
    const auth = useAuthStore()

    expect(await auth.restaurarSesion()).toBe(false)
    expect(postRefresh).not.toHaveBeenCalled()
  })

  it('restaurarSesion con una sesión ya viva → true sin tocar la red (idempotente, no la pisa)', async () => {
    stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const ok = await auth.restaurarSesion()

    expect(ok).toBe(true)
    expect(auth.token).toBe('jwt-123')
    expect(postRefresh).not.toHaveBeenCalled()
  })

  it('asegurarRestauracion es single-flight: dos llamadas comparten un solo refresh', async () => {
    stubStorage(refreshPersistido('refresh-viejo', '2099-01-01T00:00:00Z'))
    vi.mocked(postRefresh).mockResolvedValue({
      token: 'jwt-rotado',
      expiraEn: '2099-01-01T00:15:00Z',
      refreshToken: 'refresh-rotado',
      refreshExpiraEn: '2099-01-08T00:00:00Z',
    })
    vi.mocked(getMe).mockResolvedValue({ email: 'ana@example.com' })
    const auth = useAuthStore()

    const [a, b] = await Promise.all([auth.asegurarRestauracion(), auth.asegurarRestauracion()])

    expect(a).toBe(true)
    expect(b).toBe(true)
    // Dos navegaciones casi simultáneas al arrancar no pueden lanzar dos
    // refresh (el segundo rotaría el token que el primero está usando).
    expect(postRefresh).toHaveBeenCalledTimes(1)
  })

  it('cerrarSesion purga también el refresh persistido', async () => {
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(true)

    auth.cerrarSesion()

    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('borrarCuenta purga también el refresh persistido (RGPD: no queda nada en el disco)', async () => {
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    vi.mocked(deleteCuenta).mockResolvedValue(undefined)
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    await auth.borrarCuenta('superclave123')

    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })
})
