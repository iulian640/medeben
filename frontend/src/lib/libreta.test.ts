import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  CLAVE_ONBOARDING_LIBRETA,
  diaSemanaDe,
  formatearMinutos,
  horaActual,
  horaLocalDe,
  lunesDe,
  marcaOnboardingVisto,
  minutosTeoricos,
  onboardingVisto,
  sumarDias,
} from './libreta'

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('horaActual', () => {
  it('devuelve la hora del navegador en HH:mm con ceros a la izquierda', () => {
    // Date construida con la zona local: el resultado no depende de la zona del runner.
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 6, 8, 9, 5) })

    expect(horaActual()).toBe('09:05')
  })
})

describe('horaLocalDe', () => {
  it('convierte el sello del servidor a HH:mm en la zona del navegador', () => {
    const instante = new Date(2026, 6, 8, 23, 47).toISOString()

    expect(horaLocalDe(instante)).toBe('23:47')
  })
})

describe('formatearMinutos', () => {
  it('formatea horas y minutos en cristiano', () => {
    expect(formatearMinutos(450)).toBe('7 h 30 min')
    expect(formatearMinutos(480)).toBe('8 h')
    expect(formatearMinutos(45)).toBe('45 min')
    expect(formatearMinutos(0)).toBe('0 min')
  })
})

describe('sumarDias', () => {
  it('suma y resta días cruzando el mes', () => {
    expect(sumarDias('2026-07-08', 1)).toBe('2026-07-09')
    expect(sumarDias('2026-07-31', 1)).toBe('2026-08-01')
    expect(sumarDias('2026-07-01', -1)).toBe('2026-06-30')
    expect(sumarDias('2026-01-01', -1)).toBe('2025-12-31')
  })
})

describe('lunesDe', () => {
  it('devuelve el lunes de la semana de cualquier día', () => {
    expect(lunesDe('2026-07-08')).toBe('2026-07-06') // miércoles
    expect(lunesDe('2026-07-06')).toBe('2026-07-06') // el propio lunes
    expect(lunesDe('2026-07-12')).toBe('2026-07-06') // domingo, cierra la semana
  })
})

describe('diaSemanaDe', () => {
  it('da el nombre del día en castellano', () => {
    expect(diaSemanaDe('2026-07-08')).toBe('miércoles')
    expect(diaSemanaDe('2026-07-12')).toBe('domingo')
  })
})

describe('flag de onboarding', () => {
  it('con localStorage disponible, guarda y lee SOLO el flag booleano', () => {
    const datos = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (clave: string) => datos.get(clave) ?? null,
      setItem: (clave: string, valor: string) => void datos.set(clave, valor),
    })

    expect(onboardingVisto()).toBe(false)

    marcaOnboardingVisto()

    expect(onboardingVisto()).toBe(true)
    expect([...datos.entries()]).toEqual([[CLAVE_ONBOARDING_LIBRETA, '1']])
  })

  it('sin localStorage no rompe: el onboarding simplemente se repite', () => {
    vi.stubGlobal('localStorage', undefined)

    expect(onboardingVisto()).toBe(false)
    expect(() => marcaOnboardingVisto()).not.toThrow()
  })

  it('con localStorage que lanza (modo privado estricto) tampoco rompe', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('SecurityError')
      },
      setItem: () => {
        throw new Error('SecurityError')
      },
    })

    expect(onboardingVisto()).toBe(false)
    expect(() => marcaOnboardingVisto()).not.toThrow()
  })
})

describe('minutosTeoricos', () => {
  it('suma los tramos de un día del horario', () => {
    expect(minutosTeoricos({ tramos: [{ entrada: '09:00', salida: '17:00' }] })).toBe(480)
    expect(
      minutosTeoricos({
        tramos: [
          { entrada: '12:00', salida: '16:00' },
          { entrada: '20:00', salida: '23:30' },
        ],
      }),
    ).toBe(450)
  })

  it('un día libre (sin tramos) son 0 minutos', () => {
    expect(minutosTeoricos({ tramos: [] })).toBe(0)
  })

  it('un tramo con salida anterior a la entrada cruza la medianoche', () => {
    // Turno de cierre 20:00 → 02:00: 6 horas, no un negativo.
    expect(minutosTeoricos({ tramos: [{ entrada: '20:00', salida: '02:00' }] })).toBe(360)
  })
})
