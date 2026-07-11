import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  CLAVE_SESION_PERSISTIDA,
  borrarSesionPersistida,
  guardarSesionPersistida,
  leerSesionPersistida,
} from './sesionPersistida'

/** localStorage de mentira respaldado por un Map, para no depender del entorno. */
function localStorageFalso(inicial: Record<string, string> = {}) {
  const datos = new Map<string, string>(Object.entries(inicial))
  return {
    almacen: {
      getItem: (clave: string) => datos.get(clave) ?? null,
      setItem: (clave: string, valor: string) => void datos.set(clave, valor),
      removeItem: (clave: string) => void datos.delete(clave),
    },
    datos,
  }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('sesionPersistida', () => {
  it('guarda y lee { refreshToken, refreshExpiraEn } bajo una sola clave', () => {
    const { almacen, datos } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)

    guardarSesionPersistida({ refreshToken: 'refresh-1', refreshExpiraEn: '2026-07-17T00:00:00Z' })

    // Todo bajo UNA clave: nada del access token ni del email se persiste.
    expect([...datos.keys()]).toEqual([CLAVE_SESION_PERSISTIDA])
    expect(leerSesionPersistida()).toEqual({
      refreshToken: 'refresh-1',
      refreshExpiraEn: '2026-07-17T00:00:00Z',
    })
  })

  it('borra lo persistido', () => {
    const { almacen, datos } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)
    guardarSesionPersistida({ refreshToken: 'refresh-1', refreshExpiraEn: '2026-07-17T00:00:00Z' })

    borrarSesionPersistida()

    expect(datos.size).toBe(0)
    expect(leerSesionPersistida()).toBeNull()
  })

  it('sin localStorage no rompe: guardar/leer/borrar son inertes', () => {
    vi.stubGlobal('localStorage', undefined)

    expect(() =>
      guardarSesionPersistida({ refreshToken: 'r', refreshExpiraEn: '2026-07-17T00:00:00Z' }),
    ).not.toThrow()
    expect(leerSesionPersistida()).toBeNull()
    expect(() => borrarSesionPersistida()).not.toThrow()
  })

  it('con localStorage que lanza (modo privado estricto) tampoco rompe', () => {
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

    expect(() =>
      guardarSesionPersistida({ refreshToken: 'r', refreshExpiraEn: '2026-07-17T00:00:00Z' }),
    ).not.toThrow()
    expect(leerSesionPersistida()).toBeNull()
    expect(() => borrarSesionPersistida()).not.toThrow()
  })

  it('JSON corrupto → null y purga la clave (no se confía en basura)', () => {
    const { almacen, datos } = localStorageFalso({ [CLAVE_SESION_PERSISTIDA]: 'no-es-json{' })
    vi.stubGlobal('localStorage', almacen)

    expect(leerSesionPersistida()).toBeNull()
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('shape inválido (falta un campo o no es string) → null y purga la clave', () => {
    for (const basura of [
      JSON.stringify({ refreshToken: 'solo-uno' }),
      JSON.stringify({ refreshToken: 1, refreshExpiraEn: '2026-07-17T00:00:00Z' }),
      JSON.stringify({ refreshExpiraEn: '2026-07-17T00:00:00Z' }),
      JSON.stringify(['array', 'raro']),
      JSON.stringify('cadena-suelta'),
      JSON.stringify(null),
    ]) {
      const { almacen, datos } = localStorageFalso({ [CLAVE_SESION_PERSISTIDA]: basura })
      vi.stubGlobal('localStorage', almacen)

      expect(leerSesionPersistida()).toBeNull()
      expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
    }
  })
})
