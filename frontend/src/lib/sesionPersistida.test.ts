import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  CLAVE_SESION_PERSISTIDA,
  borrarSesionPersistida,
  borrarSesionPersistidaSi,
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

  it('borrado condicional: purga si el refresh persistido es el esperado', () => {
    const { almacen, datos } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)
    guardarSesionPersistida({ refreshToken: 'R1', refreshExpiraEn: '2099-01-01T00:00:00Z' })

    borrarSesionPersistidaSi('R1')

    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('borrado condicional: NO purga si otra pestaña ya rotó a otro refresh', () => {
    // Coordinación entre pestañas (issue #220): la clave es compartida. Si otra
    // pestaña rotó el token bajo la misma clave, su refresh vigente se respeta.
    const { almacen, datos } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)
    guardarSesionPersistida({ refreshToken: 'R2', refreshExpiraEn: '2099-01-08T00:00:00Z' })

    borrarSesionPersistidaSi('R1')

    expect(datos.get(CLAVE_SESION_PERSISTIDA)).toContain('R2')
  })

  it('borrado condicional: sin nada persistido no rompe y queda vacío', () => {
    const { almacen, datos } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)

    expect(() => borrarSesionPersistidaSi('R1')).not.toThrow()
    expect(datos.size).toBe(0)
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
