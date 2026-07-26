import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAnexoUbicacionMes, getInformeMes } from './resumen'

vi.mock('./api', () => ({
  api: { get: vi.fn(), getBlob: vi.fn() },
}))

import { api } from './api'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('getInformeMes', () => {
  it('sin pedir ubicación (por defecto), el informe es el de siempre', async () => {
    await getInformeMes('2026-07')

    expect(api.getBlob).toHaveBeenCalledExactlyOnceWith('/informes/mes/2026-07')
  })

  it('con la casilla desmarcada explícitamente, tampoco manda el parámetro', async () => {
    await getInformeMes('2026-07', false)

    expect(api.getBlob).toHaveBeenCalledExactlyOnceWith('/informes/mes/2026-07')
  })

  it('con la casilla marcada, pide el informe CON ubicación', async () => {
    await getInformeMes('2026-07', true)

    expect(api.getBlob).toHaveBeenCalledExactlyOnceWith('/informes/mes/2026-07?ubicacion=true')
  })
})

describe('getAnexoUbicacionMes', () => {
  it('pide el anexo técnico del mes (único canal por el que salen coordenadas)', async () => {
    await getAnexoUbicacionMes('2026-07')

    expect(api.getBlob).toHaveBeenCalledExactlyOnceWith('/informes/mes/2026-07/anexo-ubicacion')
  })
})
