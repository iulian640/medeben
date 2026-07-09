import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '../services/api'
import type { ResumenMensual } from '../services/resumen'
import { useResumenStore } from './resumen'

vi.mock('../services/resumen', () => ({
  getResumenMes: vi.fn(),
}))

import { getResumenMes } from '../services/resumen'

function resumenServidor(mes: string): ResumenMensual {
  return {
    mes,
    minutosTeoricos: 9600,
    minutosReales: 9780,
    horasExtra: { minutos: 180, horas: 3 },
    deficitInformativo: { minutos: 0, horas: 0 },
    diasSinCalcular: 0,
    contadoresPorEstado: { COMPLETO: 20, HUECO: 2 },
    importeEstimado: {
      horasExtra: 3,
      precioHora: 10.9,
      importe: 32.7,
      salarioBaseAplicado: 1250.91,
      salarioRealUsado: false,
      desglose: {
        salarioBaseMensual: 1250.91,
        mensualidades: 14,
        plusesAnuales: 0,
        divisorHoras: 1800,
        esDivisorExplicito: false,
        valorHora: 10.9,
      },
      citas: [{ texto: 'art. 35.1 ET', url: null }],
    },
    topeAnual: { horas: 80, acumuladoAnioHoras: 3, citas: [] },
    avisos: [],
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('resumen store', () => {
  it('arranca en el mes actual y carga el resumen del backend', async () => {
    vi.mocked(getResumenMes).mockImplementation((m) => Promise.resolve(resumenServidor(m)))
    const resumen = useResumenStore()

    await resumen.cargar()

    expect(resumen.esMesActual).toBe(true)
    expect(getResumenMes).toHaveBeenCalledWith(resumen.mes)
    expect(resumen.resumen?.importeEstimado.importe).toBe(32.7)
    expect(resumen.error).toBeNull()
    expect(resumen.incompleto).toBeNull()
  })

  it('un 422 (falta perfil/horario) es guía, no error', async () => {
    vi.mocked(getResumenMes).mockRejectedValue(
      new ApiError(422, 'API 422', {
        status: 422,
        detail: 'No has definido tu horario para ese mes',
      }),
    )
    const resumen = useResumenStore()

    await resumen.cargar()

    expect(resumen.incompleto).toBe('No has definido tu horario para ese mes')
    expect(resumen.error).toBeNull()
    expect(resumen.resumen).toBeNull()
  })

  it('un fallo de verdad (500) sale como error legible', async () => {
    vi.mocked(getResumenMes).mockRejectedValue(
      new ApiError(500, 'API 500', { status: 500, detail: 'Error interno' }),
    )
    const resumen = useResumenStore()

    await resumen.cargar()

    expect(resumen.error).toBe('Error interno')
    expect(resumen.incompleto).toBeNull()
    expect(resumen.cargando).toBe(false)
  })

  it('navega al mes anterior y no deja pasar del mes actual', async () => {
    vi.mocked(getResumenMes).mockImplementation((m) => Promise.resolve(resumenServidor(m)))
    const resumen = useResumenStore()
    const mesActual = resumen.mes

    await resumen.mesAnterior()
    expect(resumen.esMesActual).toBe(false)
    expect(resumen.mes < mesActual).toBe(true)

    await resumen.mesSiguiente()
    expect(resumen.mes).toBe(mesActual)

    // Ya en el mes actual: el siguiente no dispara ninguna petición más.
    const llamadas = vi.mocked(getResumenMes).mock.calls.length
    await resumen.mesSiguiente()
    expect(vi.mocked(getResumenMes).mock.calls.length).toBe(llamadas)
  })

  it('una respuesta vieja no pisa una navegación más nueva (guard anti-carrera)', async () => {
    let resuelveLenta!: (r: ResumenMensual) => void
    vi.mocked(getResumenMes)
      .mockReturnValueOnce(new Promise((res) => (resuelveLenta = res)))
      .mockImplementation((m) => Promise.resolve(resumenServidor(m)))
    const resumen = useResumenStore()

    const lenta = resumen.cargar()
    await resumen.mesAnterior()
    const mesElegido = resumen.mes

    resuelveLenta(resumenServidor('2020-01'))
    await lenta

    expect(resumen.mes).toBe(mesElegido)
    expect(resumen.resumen?.mes).toBe(mesElegido)
  })

  it('limpiar resetea todo al cerrar sesión (importes = dato personal)', async () => {
    vi.mocked(getResumenMes).mockImplementation((m) => Promise.resolve(resumenServidor(m)))
    const resumen = useResumenStore()
    await resumen.cargar()

    resumen.limpiar()

    expect(resumen.resumen).toBeNull()
    expect(resumen.esMesActual).toBe(true)
  })
})
