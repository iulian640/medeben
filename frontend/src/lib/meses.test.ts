import { describe, expect, it } from 'vitest'
import { etiquetaMes, mesDe, sumarMeses } from './meses'

describe('meses (yyyy-MM)', () => {
  it('mesDe recorta la fecha ISO a su mes', () => {
    expect(mesDe('2026-07-09')).toBe('2026-07')
  })

  it('sumarMeses navega hacia atrás y hacia delante, cruzando el año', () => {
    expect(sumarMeses('2026-07', -1)).toBe('2026-06')
    expect(sumarMeses('2026-01', -1)).toBe('2025-12')
    expect(sumarMeses('2025-12', 1)).toBe('2026-01')
    expect(sumarMeses('2026-07', -12)).toBe('2025-07')
  })

  it('etiquetaMes habla en cristiano', () => {
    expect(etiquetaMes('2026-07')).toBe('julio de 2026')
    expect(etiquetaMes('2025-12')).toBe('diciembre de 2025')
  })
})
