import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '../services/api'
import type { PerfilGuardado } from '../services/perfilUsuario'
import { useAuthStore } from './auth'
import { useCuentaStore } from './cuenta'

vi.mock('../services/perfilUsuario', () => ({
  getPerfilUsuario: vi.fn(),
  putPerfilUsuario: vi.fn(),
}))
vi.mock('../services/convenios', () => ({
  getProvincias: vi.fn(),
  getPuestos: vi.fn(),
}))
vi.mock('../services/auth', () => ({
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
}))

import { getPerfilUsuario, putPerfilUsuario } from '../services/perfilUsuario'
import { getProvincias, getPuestos } from '../services/convenios'
import { postLogin } from '../services/auth'

const perfilServidor: PerfilGuardado = {
  provincia: 'Madrid',
  subsector: 'hosteleria',
  convenioId: 'madrid-hosteleria',
  puestoId: 'cocinero',
  dimensiones: { nivel: 'III', claseEmpresa: 'A' },
  salarioBaseMensual: 1500,
  plusesAnuales: 600,
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  vi.mocked(getProvincias).mockResolvedValue(['Madrid', 'Cuenca'])
  vi.mocked(getPuestos).mockResolvedValue([
    { id: 'cocinero', etiqueta: 'Cocinero/a' },
    { id: 'camarero', etiqueta: 'Camarero/a' },
  ])
})

describe('cuenta store', () => {
  it('cargar rellena el formulario con el perfil del servidor', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const cuenta = useCuentaStore()

    await cuenta.cargar()

    expect(cuenta.provincia).toBe('Madrid')
    expect(cuenta.subsector).toBe('hosteleria')
    expect(cuenta.puestoId).toBe('cocinero')
    expect(cuenta.salarioBaseMensual).toBe(1500)
    expect(cuenta.plusesAnuales).toBe(600)
    expect(cuenta.convenioId).toBe('madrid-hosteleria')
    expect(cuenta.sinPerfil).toBe(false)
    expect(cuenta.error).toBeNull()
  })

  it('cargar con 404 (todavía sin perfil) no es un error: formulario vacío', async () => {
    vi.mocked(getPerfilUsuario).mockRejectedValue(
      new ApiError(404, 'API 404', { status: 404, detail: 'Todavía no has creado tu perfil' }),
    )
    const cuenta = useCuentaStore()

    await cuenta.cargar()

    expect(cuenta.sinPerfil).toBe(true)
    expect(cuenta.error).toBeNull()
    expect(cuenta.provincia).toBeNull()
  })

  it('cargar con otro error lo expone legible', async () => {
    vi.mocked(getPerfilUsuario).mockRejectedValue(
      new ApiError(500, 'API 500', { status: 500, detail: 'Error interno' }),
    )
    const cuenta = useCuentaStore()

    await cuenta.cargar()

    expect(cuenta.error).toBe('Error interno')
  })

  it('guardar manda SIEMPRE el objeto completo (el PUT es full-replace)', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    vi.mocked(putPerfilUsuario).mockResolvedValue({ ...perfilServidor, salarioBaseMensual: 1600 })
    const cuenta = useCuentaStore()
    await cuenta.cargar()

    cuenta.salarioBaseMensual = 1600
    await cuenta.guardar()

    expect(putPerfilUsuario).toHaveBeenCalledWith({
      provincia: 'Madrid',
      subsector: 'hosteleria',
      puestoId: 'cocinero',
      dimensiones: { nivel: 'III', claseEmpresa: 'A' },
      salarioBaseMensual: 1600,
      plusesAnuales: 600,
    })
    expect(cuenta.guardado).toBe(true)
    expect(cuenta.salarioBaseMensual).toBe(1600)
  })

  it('si cambia el puesto, las dimensiones guardadas del puesto viejo no se reenvían', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    vi.mocked(putPerfilUsuario).mockResolvedValue({
      ...perfilServidor,
      puestoId: 'camarero',
      dimensiones: null,
    })
    const cuenta = useCuentaStore()
    await cuenta.cargar()

    cuenta.puestoId = 'camarero'
    await cuenta.guardar()

    expect(putPerfilUsuario).toHaveBeenCalledWith(
      expect.objectContaining({ puestoId: 'camarero', dimensiones: null }),
    )
  })

  it('guardar sin provincia o subsector no llama a la API y avisa', async () => {
    vi.mocked(getPerfilUsuario).mockRejectedValue(new ApiError(404, 'API 404', null))
    const cuenta = useCuentaStore()
    await cuenta.cargar()

    await cuenta.guardar()

    expect(putPerfilUsuario).not.toHaveBeenCalled()
    expect(cuenta.error).toMatch(/provincia/i)
  })

  it('un error del servidor al guardar sale legible y no marca guardado', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    vi.mocked(putPerfilUsuario).mockRejectedValue(
      new ApiError(404, 'API 404', {
        status: 404,
        detail: "No hay convenio para la provincia 'X' y subsector 'y'",
      }),
    )
    const cuenta = useCuentaStore()
    await cuenta.cargar()

    await cuenta.guardar()

    expect(cuenta.guardado).toBe(false)
    expect(cuenta.error).toMatch(/No hay convenio/)
  })

  it('cargar con 404 limpia TODOS los campos si venían rellenos de otra cuenta', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const cuenta = useCuentaStore()
    await cuenta.cargar()
    expect(cuenta.salarioBaseMensual).toBe(1500)

    // Segunda carga: este usuario todavía no tiene perfil (404).
    vi.mocked(getPerfilUsuario).mockRejectedValue(
      new ApiError(404, 'API 404', { status: 404, detail: 'Todavía no has creado tu perfil' }),
    )
    await cuenta.cargar()

    expect(cuenta.sinPerfil).toBe(true)
    expect(cuenta.provincia).toBeNull()
    expect(cuenta.subsector).toBeNull()
    expect(cuenta.puestoId).toBeNull()
    expect(cuenta.salarioBaseMensual).toBeNull()
    expect(cuenta.plusesAnuales).toBeNull()
    expect(cuenta.convenioId).toBeNull()
  })

  it('dispositivo compartido: logout de A y login de B sin perfil → formulario limpio', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-a', expiraEn: '2026-07-09T00:00:00Z' })
    const auth = useAuthStore()
    const cuenta = useCuentaStore()
    await auth.iniciarSesion('a@example.com', 'superclave123')
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    await cuenta.cargar()
    expect(cuenta.salarioBaseMensual).toBe(1500)

    auth.cerrarSesion()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-b', expiraEn: '2026-07-09T00:00:00Z' })
    await auth.iniciarSesion('b@example.com', 'superclave123')
    vi.mocked(getPerfilUsuario).mockRejectedValue(new ApiError(404, 'API 404', null))
    await cuenta.cargar()

    expect(cuenta.sinPerfil).toBe(true)
    expect(cuenta.provincia).toBeNull()
    expect(cuenta.puestoId).toBeNull()
    expect(cuenta.salarioBaseMensual).toBeNull()
    expect(cuenta.plusesAnuales).toBeNull()
  })

  it('limpiar descarta una carga en vuelo: la respuesta tardía no repuebla el formulario', async () => {
    let resolverPerfil!: (p: PerfilGuardado) => void
    vi.mocked(getPerfilUsuario).mockReturnValue(
      new Promise((resolve) => {
        resolverPerfil = resolve
      }),
    )
    const cuenta = useCuentaStore()
    const carga = cuenta.cargar()

    cuenta.limpiar()
    resolverPerfil(perfilServidor)
    await carga

    expect(cuenta.provincia).toBeNull()
    expect(cuenta.salarioBaseMensual).toBeNull()
    expect(cuenta.cargando).toBe(false)
  })

  it('limpiar descarta un guardado en vuelo: la respuesta tardía del PUT no repuebla el formulario', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const cuenta = useCuentaStore()
    await cuenta.cargar()
    let resolverPut!: (p: PerfilGuardado) => void
    vi.mocked(putPerfilUsuario).mockReturnValue(
      new Promise((resolve) => {
        resolverPut = resolve
      }),
    )
    const guardadoEnVuelo = cuenta.guardar()

    cuenta.limpiar()
    resolverPut({ ...perfilServidor, salarioBaseMensual: 1600 })
    await guardadoEnVuelo

    expect(cuenta.provincia).toBeNull()
    expect(cuenta.salarioBaseMensual).toBeNull()
    expect(cuenta.guardado).toBe(false)
    expect(cuenta.guardando).toBe(false)
  })

  it('dispositivo compartido: el PUT tardío de A no pisa el perfil recién cargado de B', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const cuenta = useCuentaStore()
    await cuenta.cargar()
    let resolverPut!: (p: PerfilGuardado) => void
    vi.mocked(putPerfilUsuario).mockReturnValue(
      new Promise((resolve) => {
        resolverPut = resolve
      }),
    )
    const guardadoDeA = cuenta.guardar()

    // A cierra sesión con su PUT aún en vuelo; B entra y carga su propio perfil.
    cuenta.limpiar()
    vi.mocked(getPerfilUsuario).mockResolvedValue({
      ...perfilServidor,
      provincia: 'Cuenca',
      convenioId: 'cuenca-hosteleria',
      salarioBaseMensual: 1400,
    })
    await cuenta.cargar()

    // Llega tarde la respuesta del PUT de A: los datos de B quedan intactos.
    resolverPut({ ...perfilServidor, salarioBaseMensual: 9999 })
    await guardadoDeA

    expect(cuenta.provincia).toBe('Cuenca')
    expect(cuenta.convenioId).toBe('cuenca-hosteleria')
    expect(cuenta.salarioBaseMensual).toBe(1400)
    expect(cuenta.guardado).toBe(false)
  })

  it('un error tardío del PUT tras limpiar no se muestra al siguiente usuario', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const cuenta = useCuentaStore()
    await cuenta.cargar()
    let rechazarPut!: (e: unknown) => void
    vi.mocked(putPerfilUsuario).mockReturnValue(
      new Promise((_resolve, reject) => {
        rechazarPut = reject
      }),
    )
    const guardadoEnVuelo = cuenta.guardar()

    cuenta.limpiar()
    rechazarPut(new ApiError(500, 'API 500', { status: 500, detail: 'Error interno' }))
    await guardadoEnVuelo

    expect(cuenta.error).toBeNull()
    expect(cuenta.guardando).toBe(false)
  })

  it('editar un campo tras guardar retira la marca de guardado', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    vi.mocked(putPerfilUsuario).mockResolvedValue(perfilServidor)
    const cuenta = useCuentaStore()
    await cuenta.cargar()
    await cuenta.guardar()
    expect(cuenta.guardado).toBe(true)

    cuenta.marcarEdicion()

    expect(cuenta.guardado).toBe(false)
  })
})
