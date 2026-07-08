import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getHorarioSemana } from './horario'

vi.mock('./api', () => ({
  api: { get: vi.fn() },
}))

import { api } from './api'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('servicio de horario', () => {
  it('getHorarioSemana pide la semana por su lunes', async () => {
    await getHorarioSemana('2026-07-06')

    expect(api.get).toHaveBeenCalledWith('/horario/semana/2026-07-06')
  })
})
