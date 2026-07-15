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
  postVerificaEmail: vi.fn(),
  postReenviaVerificacion: vi.fn(),
}))
vi.mock('../services/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/api')>()),
  setAuthToken: vi.fn(),
}))

import {
  deleteCuenta,
  getMe,
  postLogin,
  postLogout,
  postReenviaVerificacion,
  postRefresh,
  postRegistro,
  postVerificaEmail,
} from '../services/auth'
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
function refreshPersistido(
  refreshToken: string,
  refreshExpiraEn: string,
  familia = 'familia-test',
): Record<string, string> {
  return { [CLAVE_SESION_PERSISTIDA]: JSON.stringify({ refreshToken, refreshExpiraEn, familia }) }
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

  it('registro OK YA NO inicia sesión (verificación de email): guarda el email para prefill', async () => {
    // Decisión de producto (verificación de email, B4): el 201 de /registro es
    // uniforme y no significa "cuenta lista para entrar" — la pantalla "revisa
    // tu correo" necesita SOLO el email al que hemos escrito, sin credencial.
    vi.mocked(postRegistro).mockResolvedValue({ email: 'ana@example.com' })
    const auth = useAuthStore()

    const ok = await auth.registrarse('ana@example.com', 'superclave123')

    expect(ok).toBe(true)
    expect(postRegistro).toHaveBeenCalledWith('ana@example.com', 'superclave123')
    expect(postLogin).not.toHaveBeenCalled()
    expect(auth.autenticado).toBe(false)
    expect(auth.emailRecienRegistrado).toBe('ana@example.com')
  })

  it('registro KO: error legible, sin login y sin guardar el email para prefill', async () => {
    // El backend ya no distingue "email ya registrado" en la respuesta (201
    // uniforme siempre): un error aquí solo puede ser de red o del servidor.
    vi.mocked(postRegistro).mockRejectedValue(
      new ApiError(500, 'API 500', { status: 500, detail: 'Error interno' }),
    )
    const auth = useAuthStore()

    const ok = await auth.registrarse('ana@example.com', 'superclave123')

    expect(ok).toBe(false)
    expect(auth.error).toBeTruthy()
    expect(postLogin).not.toHaveBeenCalled()
    expect(auth.emailRecienRegistrado).toBeNull()
  })

  it('cerrarSesion limpia todo y desregistra el token del cliente API', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    await auth.cerrarSesion()

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

    await auth.cerrarSesion()

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

    await auth.cerrarSesion()

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
    await auth.cerrarSesion()
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
    // La sección de sesión arranca en una microtarea: se espera a que el POST
    // esté DE VERDAD en vuelo antes de cerrar sesión (si no, la cola local
    // haría que el refresh ni llegara a salir — otro escenario, otro test).
    await vi.waitFor(() => expect(postRefresh).toHaveBeenCalled())
    const cerrando = auth.cerrarSesion()
    resolverRefresh()
    await cerrando

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

    await auth.cerrarSesion()

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
      // La familia (issue #229) es un UUID aleatorio de coordinación entre
      // pestañas: no identifica al usuario ni viaja nunca al servidor.
      familia: expect.any(String),
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
    vi.mocked(getMe).mockResolvedValue({ email: 'ana@example.com', emailVerificado: false })
    const auth = useAuthStore()

    const ok = await auth.restaurarSesion()

    expect(ok).toBe(true)
    expect(auth.autenticado).toBe(true)
    expect(postRefresh).toHaveBeenCalledWith('refresh-viejo')
    expect(auth.token).toBe('jwt-rotado')
    expect(auth.email).toBe('ana@example.com')
    // El refresh rotado queda persistido, listo para la siguiente recarga.
    // La rotación CONSERVA la familia (issue #229): es la misma cadena.
    expect(leerSesionPersistida()).toEqual({
      refreshToken: 'refresh-rotado',
      refreshExpiraEn: '2099-01-08T00:00:00Z',
      familia: 'familia-test',
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
    vi.mocked(getMe).mockResolvedValue({ email: 'ana@example.com', emailVerificado: false })
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

    await auth.cerrarSesion()

    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('sesionCaducada (expulsión por 401) purga también el refresh persistido', async () => {
    // Camino MÁS habitual de purga en producción: el access caduca en pleno uso,
    // el refresh en segundo plano también falla y el manejador del 401 expulsa.
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(true)

    await auth.sesionCaducada()

    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('sesionCaducada sin refresh en memoria NO toca el slot compartido', async () => {
    // Una pestaña sin refresh no tiene derecho sobre el slot: puede ser la
    // sesión superviviente de un 429 o la cadena de otra pestaña (review #229).
    const datos = stubStorage(refreshPersistido('R-de-otra', '2099-01-01T00:00:00Z', 'familia-otra'))
    const auth = useAuthStore()

    await auth.sesionCaducada()

    expect(datos.get(CLAVE_SESION_PERSISTIDA)).toContain('R-de-otra')
  })

  it('restauración fallida NO borra el refresh que otra pestaña ya rotó (coordinación entre pestañas)', async () => {
    // Dos pestañas comparten la clave. Esta arranca con R1, pero mientras su
    // refrescar viaja la OTRA pestaña rota el token a R2 y lo persiste. A esta
    // el servidor le rechaza R1 (reutilización); su limpieza NO debe clobbear
    // el R2 vigente de la otra pestaña (issue #220).
    const datos = stubStorage(refreshPersistido('R1', '2099-01-01T00:00:00Z'))
    vi.mocked(postRefresh).mockImplementation(async () => {
      datos.set(
        CLAVE_SESION_PERSISTIDA,
        JSON.stringify({
          refreshToken: 'R2',
          refreshExpiraEn: '2099-01-08T00:00:00Z',
          familia: 'familia-test',
        }),
      )
      throw new ApiError(401, 'API 401', null)
    })
    const auth = useAuthStore()

    const ok = await auth.restaurarSesion()

    expect(ok).toBe(false)
    expect(auth.autenticado).toBe(false)
    // El R2 de la otra pestaña sigue en el storage: no lo hemos pisado.
    expect(datos.get(CLAVE_SESION_PERSISTIDA)).toContain('R2')
  })

  it('la expulsión por 401 NO borra el refresh que otra pestaña ya rotó', async () => {
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    // Otra pestaña rota y re-persiste bajo la misma clave compartida.
    datos.set(
      CLAVE_SESION_PERSISTIDA,
      JSON.stringify({
        refreshToken: 'R2',
        refreshExpiraEn: '2099-01-08T00:00:00Z',
        familia: 'familia-test',
      }),
    )

    await auth.sesionCaducada()

    expect(auth.autenticado).toBe(false)
    // R2 intacto: la pestaña expulsada llevaba R1, no puede purgar el de otra.
    expect(datos.get(CLAVE_SESION_PERSISTIDA)).toContain('R2')
  })

  // --- Coordinación entre pestañas (issue #229) ---

  it('refrescar usa el refresh PERSISTIDO si otra pestaña ya lo rotó (no dispara la alarma antirrobo)', async () => {
    // Repro del issue #229: la pestaña B rotó R1 → R2 bajo la clave compartida.
    // Esta pestaña (A) sigue con R1 en memoria; al caducar su access, renovar
    // con R1 (ya gastado) haría que el servidor revocara TODAS las sesiones.
    // El arreglo: releer el persistido y renovar con la punta más nueva (R2).
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    vi.mocked(postRefresh).mockResolvedValue({
      token: 'jwt-rotado',
      expiraEn: '2099-01-01T00:15:00Z',
      refreshToken: 'R3',
      refreshExpiraEn: '2099-01-08T00:00:00Z',
    })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    // La otra pestaña rota el token compartido (conserva el resto del blob).
    const blob = JSON.parse(datos.get(CLAVE_SESION_PERSISTIDA)!) as Record<string, unknown>
    datos.set(CLAVE_SESION_PERSISTIDA, JSON.stringify({ ...blob, refreshToken: 'R2' }))

    const ok = await auth.refrescar()

    expect(ok).toBe(true)
    expect(postRefresh).toHaveBeenCalledWith('R2')
    expect(postRefresh).not.toHaveBeenCalledWith('R1')
    expect(auth.refreshToken).toBe('R3')
  })

  it('cerrarSesion revoca la punta PERSISTIDA de la cadena, no la copia gastada de memoria (sin zombis)', async () => {
    // Bug relacionado del issue #229: revocar el token de memoria (ya gastado
    // por la rotación de otra pestaña) deja viva en el servidor la rotación
    // actual — una sesión zombi que el usuario no puede cerrar.
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const blob = JSON.parse(datos.get(CLAVE_SESION_PERSISTIDA)!) as Record<string, unknown>
    datos.set(CLAVE_SESION_PERSISTIDA, JSON.stringify({ ...blob, refreshToken: 'R2' }))

    await auth.cerrarSesion()

    expect(postLogout).toHaveBeenCalledWith('R2')
    expect(postLogout).not.toHaveBeenCalledWith('R1')
    expect(auth.autenticado).toBe(false)
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('refrescar cuando otra pestaña YA cerró la sesión → false sin ir a la red', async () => {
    // El storage funciona y la clave no está: la sesión se cerró a propósito
    // en otra pestaña. Renovar con el token de memoria resucitaría una sesión
    // que el usuario quiso matar (o pediría un 401 seguro).
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    datos.delete(CLAVE_SESION_PERSISTIDA)

    const ok = await auth.refrescar()

    expect(ok).toBe(false)
    expect(postRefresh).not.toHaveBeenCalled()
    // Su copia (una punta viva huérfana) se desarma: revocada no puede
    // disparar la alarma antirrobo ni quedar de zombi.
    expect(postLogout).toHaveBeenCalledWith('R1')
  })

  it('refrescar deja el marcador enVuelo durante el POST y lo limpia al persistir el rotado', async () => {
    // Protocolo de token quemado (issue #229): si esta pestaña muere con el
    // refresh en vuelo (recarga, cierre, deploy), el marcador persistido delata
    // que el token pudo gastarse. Quien lo encuentre lo revoca (lo DESARMA) en
    // vez de reutilizarlo — un token revocado da 401 plano, nunca revocaTodas.
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    let blobDuranteVuelo: Record<string, unknown> | null = null
    vi.mocked(postRefresh).mockImplementation(async () => {
      blobDuranteVuelo = JSON.parse(datos.get(CLAVE_SESION_PERSISTIDA)!) as Record<string, unknown>
      return { token: 'jwt-rotado', expiraEn: '2099-01-01T00:15:00Z', refreshToken: 'R2', refreshExpiraEn: '2099-01-08T00:00:00Z' }
    })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const ok = await auth.refrescar()

    expect(ok).toBe(true)
    expect(blobDuranteVuelo).toMatchObject({ refreshToken: 'R1', enVuelo: 'R1' })
    const blobFinal = JSON.parse(datos.get(CLAVE_SESION_PERSISTIDA)!) as Record<string, unknown>
    expect(blobFinal.refreshToken).toBe('R2')
    expect(blobFinal.enVuelo).toBeUndefined()
  })

  it('refrescar que encuentra un marcador enVuelo NO reutiliza el token: lo desarma y expulsa', async () => {
    // Otra pestaña murió con el refresh en vuelo: su token pudo gastarse en el
    // servidor sin que la respuesta llegara. Reutilizarlo dispararía
    // revocaTodas (adiós a la sesión del móvil). Se revoca y toca re-login.
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const blob = JSON.parse(datos.get(CLAVE_SESION_PERSISTIDA)!) as Record<string, unknown>
    datos.set(
      CLAVE_SESION_PERSISTIDA,
      JSON.stringify({ ...blob, refreshToken: 'R2', enVuelo: 'R2' }),
    )

    const ok = await auth.refrescar()

    expect(ok).toBe(false)
    expect(postRefresh).not.toHaveBeenCalled()
    expect(postLogout).toHaveBeenCalledWith('R2')
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('refrescar con error de RED (no un 401) desarma el token: su estado en el servidor es desconocido', async () => {
    // Un timeout o un abort en pleno POST deja la duda: ¿llegó a rotarse? Si se
    // dejara el token persistido y armado, la siguiente pestaña lo reutilizaría
    // y podría disparar revocaTodas. Desarmar + purgar = re-login, no catástrofe.
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    vi.mocked(postRefresh).mockRejectedValue(new TypeError('Failed to fetch'))
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const ok = await auth.refrescar()

    expect(ok).toBe(false)
    expect(postLogout).toHaveBeenCalledWith('R1')
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('refrescar con un 401 limpio NO desarma nada: el servidor ya decidió que el token está muerto', async () => {
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    vi.mocked(postRefresh).mockRejectedValue(new ApiError(401, 'API 401', null))
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const ok = await auth.refrescar()

    expect(ok).toBe(false)
    expect(postLogout).not.toHaveBeenCalled()
    // El blob del token muerto sí se purga: no hay nada que restaurar ahí.
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('refrescar con el slot ocupado por OTRA familia (re-login ajeno) → false sin adoptar nada', async () => {
    // Otra pestaña inició sesión NUEVA (quizá otra cuenta) y el slot compartido
    // ya no es de esta cadena. Adoptar ese token mezclaría cuentas — jamás.
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    datos.set(
      CLAVE_SESION_PERSISTIDA,
      JSON.stringify({ refreshToken: 'R-ajena', refreshExpiraEn: '2099-01-01T00:00:00Z', familia: 'otra-familia' }),
    )

    const ok = await auth.refrescar()

    expect(ok).toBe(false)
    expect(postRefresh).not.toHaveBeenCalled()
    // Nuestra copia (cadena abandonada) se desarma; el blob ajeno ni se toca.
    expect(postLogout).toHaveBeenCalledWith('R1')
    expect(postLogout).not.toHaveBeenCalledWith('R-ajena')
    expect(datos.get(CLAVE_SESION_PERSISTIDA)).toContain('R-ajena')
  })

  it('refrescar con el storage ROTO usa el token de memoria (modo privado: comportamiento pre-persistencia)', async () => {
    // Sin storage no hay pestañas que coordinar: la sesión vive solo en la
    // memoria de esta pestaña, como antes del issue #220.
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('SecurityError')
      },
      setItem: () => {
        throw new Error('SecurityError')
      },
      removeItem: () => {
        throw new Error('SecurityError')
      },
    })
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    vi.mocked(postRefresh).mockResolvedValue({ token: 'jwt-rotado', expiraEn: '2099-01-01T00:15:00Z', refreshToken: 'R2', refreshExpiraEn: '2099-01-08T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const ok = await auth.refrescar()

    expect(ok).toBe(true)
    expect(postRefresh).toHaveBeenCalledWith('R1')
    expect(postLogout).not.toHaveBeenCalled()
  })

  it('refrescar que no puede escribir el marcador NO arriesga el POST: desarma y expulsa', async () => {
    // El storage se leía bien pero deja de escribir (cuota llena): sin marcador
    // no hay red de seguridad si morimos en vuelo, y sin re-persistencia las
    // demás pestañas reutilizarían el token gastado. Mejor un re-login.
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const almacenSoloLectura = {
      getItem: (clave: string) => datos.get(clave) ?? null,
      setItem: () => {
        throw new Error('QuotaExceededError')
      },
      removeItem: (clave: string) => void datos.delete(clave),
    }
    vi.stubGlobal('localStorage', almacenSoloLectura)

    const ok = await auth.refrescar()

    expect(ok).toBe(false)
    expect(postRefresh).not.toHaveBeenCalled()
    expect(postLogout).toHaveBeenCalledWith('R1')
  })

  it('iniciarSesion con una sesión previa persistida la revoca antes de ocupar el slot (sin zombis)', async () => {
    // Un login nuevo sustituye a la sesión que hubiera en el navegador. Si su
    // punta no se revocara, quedaría viva en el servidor 7 días sin que nadie
    // pudiera usarla ni cerrarla.
    const datos = stubStorage(refreshPersistido('R-vieja', '2099-01-01T00:00:00Z', 'familia-vieja'))
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R-nueva', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()

    const ok = await auth.iniciarSesion('ana@example.com', 'superclave123')

    expect(ok).toBe(true)
    expect(postLogout).toHaveBeenCalledWith('R-vieja')
    const blob = JSON.parse(datos.get(CLAVE_SESION_PERSISTIDA)!) as Record<string, unknown>
    expect(blob.refreshToken).toBe('R-nueva')
    // Cadena nueva, familia nueva: la vieja no puede confundirse con esta.
    expect(blob.familia).not.toBe('familia-vieja')
  })

  it('un login FALLIDO no purga la sesión persistida de otra pestaña', async () => {
    // En la pantalla de login de la pestaña A alguien se equivoca de contraseña
    // mientras la pestaña B sigue dentro: el blob de B no puede pagar el error.
    const datos = stubStorage(refreshPersistido('R-de-b', '2099-01-01T00:00:00Z', 'familia-b'))
    vi.mocked(postLogin).mockRejectedValue(
      new ApiError(401, 'API 401', { status: 401, detail: 'Email o contraseña incorrectos' }),
    )
    const auth = useAuthStore()

    const ok = await auth.iniciarSesion('ana@example.com', 'laMala1234')

    expect(ok).toBe(false)
    expect(datos.get(CLAVE_SESION_PERSISTIDA)).toContain('R-de-b')
    expect(postLogout).not.toHaveBeenCalled()
  })

  it('dos logins estrenan familias distintas (cada cadena tiene la suya)', async () => {
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()

    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const familia1 = (JSON.parse(datos.get(CLAVE_SESION_PERSISTIDA)!) as Record<string, unknown>).familia
    await auth.cerrarSesion()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const familia2 = (JSON.parse(datos.get(CLAVE_SESION_PERSISTIDA)!) as Record<string, unknown>).familia

    expect(familia1).toBeTruthy()
    expect(familia2).toBeTruthy()
    expect(familia1).not.toBe(familia2)
  })

  it('cerrarSesion con el slot vacío (otra pestaña ya lo cerró) revoca su propia copia sin romper', async () => {
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    datos.delete(CLAVE_SESION_PERSISTIDA)

    await auth.cerrarSesion()

    expect(postLogout).toHaveBeenCalledWith('R1')
    expect(auth.autenticado).toBe(false)
  })

  it('cerrarSesion con el slot de OTRA familia revoca solo su copia y NO toca la sesión ajena', async () => {
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    datos.set(
      CLAVE_SESION_PERSISTIDA,
      JSON.stringify({ refreshToken: 'R-ajena', refreshExpiraEn: '2099-01-01T00:00:00Z', familia: 'otra-familia' }),
    )

    await auth.cerrarSesion()

    expect(postLogout).toHaveBeenCalledWith('R1')
    expect(postLogout).not.toHaveBeenCalledWith('R-ajena')
    expect(datos.get(CLAVE_SESION_PERSISTIDA)).toContain('R-ajena')
  })

  it('restaurarSesion con un marcador enVuelo huérfano → false sin red, desarma y purga', async () => {
    // Recarga en pleno refresh (o crash, o deploy): el arranque siguiente
    // encuentra el marcador. El token pudo gastarse: desarmarlo y pedir login.
    const datos = stubStorage()
    datos.set(
      CLAVE_SESION_PERSISTIDA,
      JSON.stringify({ refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z', familia: 'familia-test', enVuelo: 'R1' }),
    )
    const auth = useAuthStore()

    const ok = await auth.restaurarSesion()

    expect(ok).toBe(false)
    expect(auth.autenticado).toBe(false)
    expect(postRefresh).not.toHaveBeenCalled()
    expect(postLogout).toHaveBeenCalledWith('R1')
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('un 429 del rate limiter NO destruye la sesión: desarma el marcador y conserva el blob', async () => {
    // El filtro de rate limit corta ANTES de AuthService: seguro que no rotó y
    // el token sigue vivo. Revocarlo por un throttle transitorio (varias
    // pestañas renovando a la vez tras el WiFi de una oficina) tiraba la
    // sesión del navegador entero (review #229). Ahora el blob sobrevive: la
    // próxima recarga restaura la sesión en silencio pasada la ventana.
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    vi.mocked(postRefresh).mockRejectedValue(new ApiError(429, 'API 429', null))
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const ok = await auth.refrescar()

    expect(ok).toBe(false)
    expect(postLogout).not.toHaveBeenCalled()
    const blob = JSON.parse(datos.get(CLAVE_SESION_PERSISTIDA)!) as Record<string, unknown>
    expect(blob.refreshToken).toBe('R1')
    expect(blob.enVuelo).toBeUndefined()
    // La pestaña suelta su copia: la expulsión posterior no purga el slot.
    expect(auth.refreshToken).toBeNull()
    await auth.sesionCaducada()
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(true)
  })

  it('cerrarSesion purga y revoca SÍNCRONAMENTE: la pestaña puede morir tras el click', async () => {
    // Regresión cazada en la review (#229): si la purga esperase al candado,
    // cerrar la pestaña justo tras el click dejaría en un dispositivo
    // compartido un refresh VIVO, persistido y sin revocar.
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const pendiente = auth.cerrarSesion()

    // Antes de cualquier await: slot vacío y revocación ya disparada.
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
    expect(postLogout).toHaveBeenCalledWith('R1')
    await pendiente
  })

  it('cerrarSesion repesca la rotación que resucitó el slot durante la purga síncrona', async () => {
    // Otra pestaña estaba rotando mientras esta cerraba sesión: al terminar,
    // su re-persistencia resucita el slot. La sección con candado lo detecta,
    // revoca esa punta y vuelve a vaciar el slot: el logout gana siempre.
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const blob = JSON.parse(datos.get(CLAVE_SESION_PERSISTIDA)!) as Record<string, unknown>

    const pendiente = auth.cerrarSesion()
    // La rotación de la otra pestaña aterriza tras la purga síncrona.
    datos.set(
      CLAVE_SESION_PERSISTIDA,
      JSON.stringify({ refreshToken: 'R3', refreshExpiraEn: '2099-01-08T00:00:00Z', familia: blob.familia }),
    )
    await pendiente

    expect(postLogout).toHaveBeenCalledWith('R3')
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('iniciarSesion con la escritura del slot rota deja el slot limpio (sin blob previo que auto-expulse)', async () => {
    // Si la escritura falla (cuota) y quedara el blob de la sesión previa (ya
    // revocada), el siguiente refresh lo tomaría por un slot ajeno y esta
    // pestaña se auto-expulsaría (review #229). Slot limpio y solo-memoria.
    const datos = new Map<string, string>([
      [CLAVE_SESION_PERSISTIDA, JSON.stringify({ refreshToken: 'R-vieja', refreshExpiraEn: '2099-01-01T00:00:00Z', familia: 'familia-vieja' })],
    ])
    vi.stubGlobal('localStorage', {
      getItem: (clave: string) => datos.get(clave) ?? null,
      setItem: () => {
        throw new Error('QuotaExceededError')
      },
      removeItem: (clave: string) => void datos.delete(clave),
    })
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R-nueva', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()

    const ok = await auth.iniciarSesion('ana@example.com', 'superclave123')

    expect(ok).toBe(true)
    expect(auth.autenticado).toBe(true)
    expect(postLogout).toHaveBeenCalledWith('R-vieja')
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('si la re-persistencia del rotado falla, el marcador NO queda atrás y la pestaña sigue', async () => {
    // Sin este cuidado, el blob con enVuelo huérfano auto-expulsaba a la
    // propia pestaña sana en su siguiente renovación y dejaba el token rotado
    // vivo sin revocar (review #229).
    const datos = new Map<string, string>()
    let escrituras = 0
    vi.stubGlobal('localStorage', {
      getItem: (clave: string) => datos.get(clave) ?? null,
      setItem: (clave: string, valor: string) => {
        if (clave === CLAVE_SESION_PERSISTIDA) {
          escrituras += 1
          // 1ª: login. 2ª: marcador enVuelo. 3ª: re-persistencia del rotado → cuota.
          if (escrituras >= 3) {
            throw new Error('QuotaExceededError')
          }
        }
        datos.set(clave, valor)
      },
      removeItem: (clave: string) => void datos.delete(clave),
    })
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    vi.mocked(postRefresh).mockResolvedValue({ token: 'jwt-rotado', expiraEn: '2099-01-01T00:15:00Z', refreshToken: 'R2', refreshExpiraEn: '2099-01-08T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const ok = await auth.refrescar()

    expect(ok).toBe(true)
    expect(auth.refreshToken).toBe('R2')
    // Ni marcador huérfano ni blob viejo: el slot queda limpio.
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  // --- reconciliar: puesta al día al despertar la pestaña (issue #229) ---

  it('reconciliar espera al refresh en vuelo de su PROPIA pestaña (no quema su marcador)', async () => {
    // Sin Web Locks (jsdom, dev por http) el candado es un no-op: la cola
    // local intra-pestaña es lo que impide que un visibilitychange durante un
    // refresh lea el marcador enVuelo propio y lo trate como huérfano.
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    let resolverRefresh: () => void = () => {}
    vi.mocked(postRefresh).mockReturnValue(
      new Promise((resolve) => {
        resolverRefresh = () =>
          resolve({ token: 'jwt-rotado', expiraEn: '2099-01-01T00:15:00Z', refreshToken: 'R2', refreshExpiraEn: '2099-01-08T00:00:00Z' })
      }),
    )
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const renovando = auth.refrescar()
    const reconciliando = auth.reconciliar()
    resolverRefresh()

    expect(await renovando).toBe(true)
    expect(await reconciliando).toBe(false)
    expect(postLogout).not.toHaveBeenCalled()
    expect(auth.autenticado).toBe(true)
    expect(auth.refreshToken).toBe('R2')
    expect(datos.get(CLAVE_SESION_PERSISTIDA)).toContain('R2')
  })

  it('reconciliar adopta la rotación que otra pestaña hizo mientras esta dormía (misma familia)', async () => {
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const blob = JSON.parse(datos.get(CLAVE_SESION_PERSISTIDA)!) as Record<string, unknown>
    datos.set(
      CLAVE_SESION_PERSISTIDA,
      JSON.stringify({ ...blob, refreshToken: 'R2', refreshExpiraEn: '2099-02-01T00:00:00Z' }),
    )

    const expulsada = await auth.reconciliar()

    expect(expulsada).toBe(false)
    expect(auth.autenticado).toBe(true)
    expect(auth.refreshToken).toBe('R2')
    expect(auth.refreshExpiraEn).toBe('2099-02-01T00:00:00Z')
    expect(postLogout).not.toHaveBeenCalled()
  })

  it('reconciliar con el slot vacío (logout en otra pestaña) expulsa, desarma su copia y avisa', async () => {
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    datos.delete(CLAVE_SESION_PERSISTIDA)

    const expulsada = await auth.reconciliar()

    expect(expulsada).toBe(true)
    expect(auth.autenticado).toBe(false)
    expect(postLogout).toHaveBeenCalledWith('R1')
    expect(auth.aviso).toMatch(/sesión/i)
  })

  it('reconciliar con el slot de OTRA familia expulsa sin tocar la sesión ajena', async () => {
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    datos.set(
      CLAVE_SESION_PERSISTIDA,
      JSON.stringify({ refreshToken: 'R-ajena', refreshExpiraEn: '2099-01-01T00:00:00Z', familia: 'otra-familia' }),
    )

    const expulsada = await auth.reconciliar()

    expect(expulsada).toBe(true)
    expect(auth.autenticado).toBe(false)
    expect(postLogout).toHaveBeenCalledWith('R1')
    expect(postLogout).not.toHaveBeenCalledWith('R-ajena')
    expect(datos.get(CLAVE_SESION_PERSISTIDA)).toContain('R-ajena')
  })

  it('reconciliar que encuentra un marcador enVuelo huérfano desarma, purga y expulsa', async () => {
    const datos = stubStorage()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const blob = JSON.parse(datos.get(CLAVE_SESION_PERSISTIDA)!) as Record<string, unknown>
    datos.set(
      CLAVE_SESION_PERSISTIDA,
      JSON.stringify({ ...blob, refreshToken: 'R2', enVuelo: 'R2' }),
    )

    const expulsada = await auth.reconciliar()

    expect(expulsada).toBe(true)
    expect(auth.autenticado).toBe(false)
    expect(postLogout).toHaveBeenCalledWith('R2')
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('reconciliar sin sesión en esta pestaña es un no-op', async () => {
    stubStorage()
    const auth = useAuthStore()

    const expulsada = await auth.reconciliar()

    expect(expulsada).toBe(false)
    expect(postLogout).not.toHaveBeenCalled()
  })

  it('reconciliar con el storage roto es un no-op: no hay pestañas que coordinar', async () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('SecurityError')
      },
      setItem: () => {
        throw new Error('SecurityError')
      },
      removeItem: () => {
        throw new Error('SecurityError')
      },
    })
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const expulsada = await auth.reconciliar()

    expect(expulsada).toBe(false)
    expect(auth.autenticado).toBe(true)
    expect(auth.refreshToken).toBe('R1')
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

  // --- Verificación de email (B4) ---

  it('restaurarSesion también recupera emailVerificado, fresco de /me', async () => {
    stubStorage(refreshPersistido('refresh-viejo', '2099-01-01T00:00:00Z'))
    vi.mocked(postRefresh).mockResolvedValue({
      token: 'jwt-rotado',
      expiraEn: '2099-01-01T00:15:00Z',
      refreshToken: 'refresh-rotado',
      refreshExpiraEn: '2099-01-08T00:00:00Z',
    })
    vi.mocked(getMe).mockResolvedValue({ email: 'ana@example.com', emailVerificado: false })
    const auth = useAuthStore()

    await auth.restaurarSesion()

    expect(auth.emailVerificado).toBe(false)
  })

  it('actualizarEstadoVerificacion pide /me y refresca emailVerificado', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    expect(auth.emailVerificado).toBeNull()

    vi.mocked(getMe).mockResolvedValue({ email: 'ana@example.com', emailVerificado: true })
    await auth.actualizarEstadoVerificacion()

    expect(auth.emailVerificado).toBe(true)
    expect(getMe).toHaveBeenCalledTimes(1)
  })

  it('actualizarEstadoVerificacion sin sesión no llama a la red (nada que refrescar)', async () => {
    const auth = useAuthStore()

    await auth.actualizarEstadoVerificacion()

    expect(getMe).not.toHaveBeenCalled()
    expect(auth.emailVerificado).toBeNull()
  })

  it('cerrarSesion resetea emailVerificado a null (dispositivo compartido: el siguiente no hereda el aviso)', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    vi.mocked(getMe).mockResolvedValue({ email: 'ana@example.com', emailVerificado: false })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    await auth.actualizarEstadoVerificacion()
    expect(auth.emailVerificado).toBe(false)

    await auth.cerrarSesion()

    expect(auth.emailVerificado).toBeNull()
  })

  it('cerrarSesion resetea emailRecienRegistrado a null (fuga de email en tablet compartida)', async () => {
    // Se registró alguien (dejó el email para "revisa tu correo") y luego se
    // usó y cerró una sesión en la misma tablet. Ese email de registro NO puede
    // sobrevivir: el siguiente que abra /registro/revisa-correo (ruta pública,
    // botón atrás) vería una dirección ajena y el reenvío apuntaría a ella.
    vi.mocked(postRegistro).mockResolvedValue({ email: 'ana@example.com' })
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    const auth = useAuthStore()
    await auth.registrarse('ana@example.com', 'superclave123')
    expect(auth.emailRecienRegistrado).toBe('ana@example.com')
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    await auth.cerrarSesion()

    expect(auth.emailRecienRegistrado).toBeNull()
  })

  it('iniciarSesion arranca con emailVerificado en null (no arrastra el estado del usuario anterior)', async () => {
    // Dispositivo compartido: hasta que /me confirme, el aviso no debe decidirse
    // con el emailVerificado del usuario ANTERIOR. Un login nuevo lo pone a null.
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    vi.mocked(getMe).mockResolvedValue({ email: 'ana@example.com', emailVerificado: true })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    await auth.actualizarEstadoVerificacion()
    expect(auth.emailVerificado).toBe(true)

    // Segundo login (otro usuario): no debe heredar el true de Ana.
    await auth.iniciarSesion('bea@example.com', 'otraclave123')

    expect(auth.emailVerificado).toBeNull()
  })

  it('verificarEmail OK con la sesión de ESA cuenta refleja el estado fresco de /me', async () => {
    // El enlace verificado es de la cuenta que está logueada aquí: /me devolverá
    // emailVerificado=true y el aviso desaparece. No se marca optimista.
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    vi.mocked(postVerificaEmail).mockResolvedValue(undefined)
    vi.mocked(getMe).mockResolvedValue({ email: 'ana@example.com', emailVerificado: true })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    await auth.verificarEmail('un-token-valido')

    expect(postVerificaEmail).toHaveBeenCalledWith('un-token-valido')
    expect(auth.emailVerificado).toBe(true)
  })

  it('verificarEmail OK sin sesión en este navegador no inventa un estado (queda null, sin /me)', async () => {
    vi.mocked(postVerificaEmail).mockResolvedValue(undefined)
    const auth = useAuthStore()

    await auth.verificarEmail('un-token-valido')

    expect(postVerificaEmail).toHaveBeenCalledWith('un-token-valido')
    expect(getMe).not.toHaveBeenCalled()
    expect(auth.emailVerificado).toBeNull()
  })

  it('verificarEmail con OTRA cuenta logueada en la tablet NO le marca verificado (relee /me)', async () => {
    // Bea está dentro (sin verificar) y alguien abre en la misma tablet el
    // enlace de Ana. Marcar optimista ocultaría el aviso legítimo de Bea; en su
    // lugar se relee /me, que devuelve el estado REAL de Bea (sigue sin verificar).
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-bea', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-bea', refreshExpiraEn: '2099-01-01T00:00:00Z' })
    vi.mocked(postVerificaEmail).mockResolvedValue(undefined)
    vi.mocked(getMe).mockResolvedValue({ email: 'bea@example.com', emailVerificado: false })
    const auth = useAuthStore()
    await auth.iniciarSesion('bea@example.com', 'superclave123')

    await auth.verificarEmail('token-de-ana')

    expect(auth.emailVerificado).toBe(false)
  })

  it('verificarEmail KO (400) propaga el error y no toca emailVerificado', async () => {
    vi.mocked(postVerificaEmail).mockRejectedValue(
      new ApiError(400, 'API 400', { status: 400, detail: 'El enlace de verificación no es válido o ha caducado' }),
    )
    const auth = useAuthStore()

    await expect(auth.verificarEmail('token-caducado')).rejects.toThrow(ApiError)
    expect(auth.emailVerificado).toBeNull()
  })

  it('reenviarVerificacion llama al servicio con el email dado', async () => {
    vi.mocked(postReenviaVerificacion).mockResolvedValue(undefined)
    const auth = useAuthStore()

    await auth.reenviarVerificacion('ana@example.com')

    expect(postReenviaVerificacion).toHaveBeenCalledWith('ana@example.com')
  })

  it('reenviarVerificacion no lanza aunque la llamada falle (respuesta uniforme, sin revelar estados)', async () => {
    vi.mocked(postReenviaVerificacion).mockRejectedValue(new ApiError(500, 'API 500', null))
    const auth = useAuthStore()

    await expect(auth.reenviarVerificacion('ana@example.com')).resolves.toBeUndefined()
  })
})
