import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '../services/api'
import type { ApunteGuardado, EstadoDiaGuardado } from '../services/fichajes'
import type { HorarioEfectivo } from '../services/horario'
import { useAuthStore } from './auth'
import { useFichajesStore } from './fichajes'

vi.mock('../services/fichajes', () => ({
  getEstadoDia: vi.fn(),
  postApunte: vi.fn(),
}))
vi.mock('../services/horario', () => ({
  getHorarioSemana: vi.fn(),
}))
vi.mock('../services/auth', () => ({
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
}))

import { getEstadoDia, postApunte } from '../services/fichajes'
import { getHorarioSemana } from '../services/horario'
import { postLogin } from '../services/auth'

const apunteEntrada: ApunteGuardado = {
  fecha: '2026-07-08',
  tipo: 'ENTRADA',
  hora: '14:05',
  motivo: null,
  origen: 'CONFIRMADO',
  registradoEn: '2026-07-08T14:05:12+02:00',
}

const diaServidor: EstadoDiaGuardado = {
  fecha: '2026-07-08',
  estado: 'EN_CURSO',
  sellado: false,
  selladoDesde: '2026-07-23',
  minutosTrabajados: null,
  apuntes: [apunteEntrada],
}

const horarioServidor: HorarioEfectivo = {
  dias: [
    { tramos: [{ entrada: '09:00', salida: '17:00' }] },
    { tramos: [] },
    { tramos: [] },
    { tramos: [] },
    { tramos: [] },
    { tramos: [] },
    { tramos: [] },
  ],
  origen: 'SEMANA_TIPO',
  definidoEn: '2026-07-01T10:00:00+02:00',
}

function diaVacio(fecha: string): EstadoDiaGuardado {
  return {
    fecha,
    estado: 'PENDIENTE',
    sellado: false,
    selladoDesde: sumaQuince(fecha),
    minutosTrabajados: null,
    apuntes: [],
  }
}

function sumaQuince(fecha: string): string {
  const dia = Number(fecha.slice(8)) + 15
  return `${fecha.slice(0, 8)}${String(dia).padStart(2, '0')}`
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('fichajes store — el día', () => {
  it('cargarDia rellena el día con lo que dice el backend', async () => {
    vi.mocked(getEstadoDia).mockResolvedValue(diaServidor)
    const fichajes = useFichajesStore()

    await fichajes.cargarDia('2026-07-08')

    expect(fichajes.dia?.fecha).toBe('2026-07-08')
    expect(fichajes.dia?.estado).toBe('EN_CURSO')
    expect(fichajes.dia?.selladoDesde).toBe('2026-07-23')
    expect(fichajes.error).toBeNull()
    expect(fichajes.cargando).toBe(false)
  })

  it('cargarDia con error lo expone legible (RFC 7807 detail)', async () => {
    vi.mocked(getEstadoDia).mockRejectedValue(
      new ApiError(500, 'API 500', { status: 500, detail: 'Error interno' }),
    )
    const fichajes = useFichajesStore()

    await fichajes.cargarDia('2026-07-08')

    expect(fichajes.error).toBe('Error interno')
    expect(fichajes.dia).toBeNull()
  })

  it('fichar OK guarda el sello y relee el día del backend', async () => {
    vi.mocked(postApunte).mockResolvedValue(apunteEntrada)
    vi.mocked(getEstadoDia).mockResolvedValue(diaServidor)
    const fichajes = useFichajesStore()

    const apuntado = await fichajes.fichar({
      fecha: '2026-07-08',
      tipo: 'ENTRADA',
      hora: '14:05',
      motivo: null,
      rectificacionTardiaConfirmada: false,
    })

    expect(apuntado).toBe(true)
    expect(fichajes.ultimoSello?.registradoEn).toBe('2026-07-08T14:05:12+02:00')
    expect(getEstadoDia).toHaveBeenCalledWith('2026-07-08')
    expect(fichajes.dia?.estado).toBe('EN_CURSO')
    expect(fichajes.error).toBeNull()
  })

  it('un 409 (día sellado) enciende conflictoSellado y expone el detail', async () => {
    vi.mocked(postApunte).mockRejectedValue(
      new ApiError(409, 'API 409', {
        status: 409,
        detail: 'El día 2026-06-01 ya está sellado; solo cabe una rectificación tardía',
      }),
    )
    const fichajes = useFichajesStore()

    const apuntado = await fichajes.fichar({
      fecha: '2026-06-01',
      tipo: 'ENTRADA',
      hora: '10:00',
      motivo: null,
      rectificacionTardiaConfirmada: false,
    })

    expect(apuntado).toBe(false)
    expect(fichajes.conflictoSellado).toBe(true)
    expect(fichajes.error).toMatch(/ya está sellado/)
    expect(fichajes.ultimoSello).toBeNull()
  })

  it('un error que no es 409 no enciende conflictoSellado', async () => {
    vi.mocked(postApunte).mockRejectedValue(
      new ApiError(422, 'API 422', { status: 422, detail: 'Hora inválida' }),
    )
    const fichajes = useFichajesStore()

    const apuntado = await fichajes.fichar({
      fecha: '2026-07-08',
      tipo: 'ENTRADA',
      hora: '99:99',
      motivo: null,
      rectificacionTardiaConfirmada: false,
    })

    expect(apuntado).toBe(false)
    expect(fichajes.conflictoSellado).toBe(false)
    expect(fichajes.error).toBe('Hora inválida')
  })

  it('doble submit: con un POST en vuelo, el segundo toque no manda nada', async () => {
    let resolverPost!: (a: ApunteGuardado) => void
    vi.mocked(postApunte).mockReturnValue(
      new Promise((resolve) => {
        resolverPost = resolve
      }),
    )
    vi.mocked(getEstadoDia).mockResolvedValue(diaServidor)
    const fichajes = useFichajesStore()
    const peticion = {
      fecha: '2026-07-08',
      tipo: 'ENTRADA' as const,
      hora: '14:05',
      motivo: null,
      rectificacionTardiaConfirmada: false,
    }

    const primero = fichajes.fichar(peticion)
    const segundo = await fichajes.fichar(peticion)

    expect(segundo).toBe(false)
    expect(postApunte).toHaveBeenCalledTimes(1)

    resolverPost(apunteEntrada)
    expect(await primero).toBe(true)
  })

  it('si el POST entra pero la relectura falla, lo dice honestamente y devuelve true', async () => {
    vi.mocked(postApunte).mockResolvedValue(apunteEntrada)
    vi.mocked(getEstadoDia).mockRejectedValue(
      new ApiError(500, 'API 500', { status: 500, detail: 'Error interno' }),
    )
    const fichajes = useFichajesStore()

    const apuntado = await fichajes.fichar({
      fecha: '2026-07-08',
      tipo: 'ENTRADA',
      hora: '14:05',
      motivo: null,
      rectificacionTardiaConfirmada: false,
    })

    expect(apuntado).toBe(true)
    expect(fichajes.ultimoSello).not.toBeNull()
    expect(fichajes.error).toMatch(/se ha guardado/)
    expect(fichajes.conflictoSellado).toBe(false)
  })

  it('guard anti-carrera: limpiar descarta una carga en vuelo', async () => {
    let resolverDia!: (d: EstadoDiaGuardado) => void
    vi.mocked(getEstadoDia).mockReturnValue(
      new Promise((resolve) => {
        resolverDia = resolve
      }),
    )
    const fichajes = useFichajesStore()
    const carga = fichajes.cargarDia('2026-07-08')

    fichajes.limpiar()
    resolverDia(diaServidor)
    await carga

    expect(fichajes.dia).toBeNull()
    expect(fichajes.cargando).toBe(false)
  })

  it('cerrar sesión limpia la libreta desde el punto central de auth', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-1', expiraEn: '2026-07-09T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    vi.mocked(getEstadoDia).mockResolvedValue(diaServidor)
    const fichajes = useFichajesStore()
    await fichajes.cargarDia('2026-07-08')
    expect(fichajes.dia).not.toBeNull()

    auth.cerrarSesion()

    expect(fichajes.dia).toBeNull()
    expect(fichajes.semana).toEqual([])
    expect(fichajes.ultimoSello).toBeNull()
    expect(fichajes.error).toBeNull()
  })
})

describe('fichajes store — la semana', () => {
  it('cargarSemana trae los 7 días y el horario', async () => {
    vi.mocked(getEstadoDia).mockImplementation((fecha) => Promise.resolve(diaVacio(fecha)))
    vi.mocked(getHorarioSemana).mockResolvedValue(horarioServidor)
    const fichajes = useFichajesStore()

    await fichajes.cargarSemana('2026-07-06')

    expect(fichajes.lunes).toBe('2026-07-06')
    expect(fichajes.semana).toHaveLength(7)
    expect(fichajes.semana[0].fecha).toBe('2026-07-06')
    expect(fichajes.semana[6].fecha).toBe('2026-07-12')
    expect(fichajes.horario?.origen).toBe('SEMANA_TIPO')
    expect(fichajes.errorSemana).toBeNull()
  })

  it('sin horario configurado (404) no es un error: solo no hay comparación', async () => {
    vi.mocked(getEstadoDia).mockImplementation((fecha) => Promise.resolve(diaVacio(fecha)))
    vi.mocked(getHorarioSemana).mockRejectedValue(
      new ApiError(404, 'API 404', {
        status: 404,
        detail: 'No hay horario para esa semana: crea antes tu semana tipo',
      }),
    )
    const fichajes = useFichajesStore()

    await fichajes.cargarSemana('2026-07-06')

    expect(fichajes.semana).toHaveLength(7)
    expect(fichajes.horario).toBeNull()
    expect(fichajes.errorSemana).toBeNull()
  })

  it('si falla un día de la semana, el error sale legible', async () => {
    vi.mocked(getEstadoDia).mockRejectedValue(
      new ApiError(500, 'API 500', { status: 500, detail: 'Error interno' }),
    )
    vi.mocked(getHorarioSemana).mockResolvedValue(horarioServidor)
    const fichajes = useFichajesStore()

    await fichajes.cargarSemana('2026-07-06')

    expect(fichajes.errorSemana).toBe('Error interno')
    expect(fichajes.cargandoSemana).toBe(false)
  })
})
