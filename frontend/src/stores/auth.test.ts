import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '../services/api'
import { useAuthStore } from './auth'
import { useCuentaStore } from './cuenta'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
}))
vi.mock('../services/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/api')>()),
  setAuthToken: vi.fn(),
}))

import { postLogin, postRegistro } from '../services/auth'
import { setAuthToken } from '../services/api'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('auth store', () => {
  it('login OK: guarda token y email en memoria y lo registra en el cliente API', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z' })
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
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-456', expiraEn: '2026-07-09T00:00:00Z' })
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
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    auth.cerrarSesion()

    expect(auth.autenticado).toBe(false)
    expect(auth.email).toBeNull()
    expect(setAuthToken).toHaveBeenLastCalledWith(null)
  })

  it('sesionCaducada limpia la sesión y deja un aviso para la pantalla de login', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    auth.sesionCaducada()

    expect(auth.autenticado).toBe(false)
    expect(setAuthToken).toHaveBeenLastCalledWith(null)
    expect(auth.aviso).toMatch(/sesión/i)
  })

  it('cerrarSesion vacía también el store de cuenta (dispositivo compartido)', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z' })
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

  it('la expulsión por 401 (sesionCaducada) también vacía el store de cuenta', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const cuenta = useCuentaStore()
    cuenta.provincia = 'Madrid'
    cuenta.salarioBaseMensual = 1500

    auth.sesionCaducada()

    expect(cuenta.provincia).toBeNull()
    expect(cuenta.salarioBaseMensual).toBeNull()
  })

  it('un login nuevo limpia el aviso de sesión caducada anterior', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z' })
    const auth = useAuthStore()
    auth.sesionCaducada()

    await auth.iniciarSesion('ana@example.com', 'superclave123')

    expect(auth.aviso).toBeNull()
  })
})
