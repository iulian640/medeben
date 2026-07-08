import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getEstadoDia, postApunte, type ApuntePeticion } from './fichajes'

vi.mock('./api', () => ({
  api: { get: vi.fn(), post: vi.fn() },
}))

import { api } from './api'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('servicio de fichajes', () => {
  it('postApunte manda el cuerpo completo a /fichajes', async () => {
    const peticion: ApuntePeticion = {
      fecha: '2026-07-08',
      tipo: 'ENTRADA',
      hora: '14:05',
      motivo: null,
      rectificacionTardiaConfirmada: false,
    }

    await postApunte(peticion)

    expect(api.post).toHaveBeenCalledWith('/fichajes', peticion)
  })

  it('getEstadoDia pide el día por su fecha ISO', async () => {
    await getEstadoDia('2026-07-08')

    expect(api.get).toHaveBeenCalledWith('/fichajes/dia/2026-07-08')
  })
})
