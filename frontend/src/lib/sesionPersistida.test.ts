import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  CLAVE_SESION_PERSISTIDA,
  CLAVE_SONDA_ALMACENAMIENTO,
  almacenamientoFunciona,
  borrarSesionPersistida,
  borrarSesionPersistidaSi,
  borrarSesionPersistidaSiFamilia,
  generarFamilia,
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

const SESION = {
  refreshToken: 'refresh-1',
  refreshExpiraEn: '2026-07-17T00:00:00Z',
  familia: 'familia-1',
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('sesionPersistida', () => {
  it('guarda y lee { refreshToken, refreshExpiraEn, familia } bajo una sola clave', () => {
    const { almacen, datos } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)

    expect(guardarSesionPersistida(SESION)).toBe(true)

    // Todo bajo UNA clave: nada del access token ni del email se persiste.
    expect([...datos.keys()]).toEqual([CLAVE_SESION_PERSISTIDA])
    expect(leerSesionPersistida()).toEqual(SESION)
  })

  it('guarda y lee también el marcador enVuelo (protocolo de token quemado, issue #229)', () => {
    const { almacen } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)

    guardarSesionPersistida({ ...SESION, enVuelo: 'refresh-1' })

    expect(leerSesionPersistida()).toEqual({ ...SESION, enVuelo: 'refresh-1' })
  })

  it('borra lo persistido', () => {
    const { almacen, datos } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)
    guardarSesionPersistida(SESION)

    borrarSesionPersistida()

    expect(datos.size).toBe(0)
    expect(leerSesionPersistida()).toBeNull()
  })

  it('sin localStorage no rompe: guardar/leer/borrar son inertes (y guardar avisa con false)', () => {
    vi.stubGlobal('localStorage', undefined)

    expect(guardarSesionPersistida(SESION)).toBe(false)
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

    expect(guardarSesionPersistida(SESION)).toBe(false)
    expect(leerSesionPersistida()).toBeNull()
    expect(() => borrarSesionPersistida()).not.toThrow()
  })

  it('JSON corrupto → null y purga la clave (no se confía en basura)', () => {
    const { almacen, datos } = localStorageFalso({ [CLAVE_SESION_PERSISTIDA]: 'no-es-json{' })
    vi.stubGlobal('localStorage', almacen)

    expect(leerSesionPersistida()).toBeNull()
    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('la purga de un valor corrupto NO clobbea la clave si otra pestaña la reescribió entre medias', () => {
    // Compare-and-delete sobre el valor crudo: sin candado (la lectura puede
    // pasar fuera de él), purgar a ciegas podría borrar el blob recién escrito
    // por un login de otra pestaña.
    const valorNuevo = JSON.stringify(SESION)
    let lecturas = 0
    const datos = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: () => {
        lecturas += 1
        return lecturas === 1 ? 'no-es-json{' : valorNuevo
      },
      setItem: (clave: string, valor: string) => void datos.set(clave, valor),
      removeItem: vi.fn(),
    })

    expect(leerSesionPersistida()).toBeNull()

    expect(vi.mocked(globalThis.localStorage.removeItem)).not.toHaveBeenCalled()
  })

  it('borrado condicional: purga si el refresh persistido es el esperado', () => {
    const { almacen, datos } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)
    guardarSesionPersistida({ ...SESION, refreshToken: 'R1' })

    borrarSesionPersistidaSi('R1')

    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('borrado condicional: NO purga si otra pestaña ya rotó a otro refresh', () => {
    // Coordinación entre pestañas (issue #220): la clave es compartida. Si otra
    // pestaña rotó el token bajo la misma clave, su refresh vigente se respeta.
    const { almacen, datos } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)
    guardarSesionPersistida({ ...SESION, refreshToken: 'R2' })

    borrarSesionPersistidaSi('R1')

    expect(datos.get(CLAVE_SESION_PERSISTIDA)).toContain('R2')
  })

  it('borrado condicional: sin nada persistido no rompe y queda vacío', () => {
    const { almacen, datos } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)

    expect(() => borrarSesionPersistidaSi('R1')).not.toThrow()
    expect(datos.size).toBe(0)
  })

  it('borrado por familia: purga si la familia persistida es la esperada', () => {
    const { almacen, datos } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)
    guardarSesionPersistida(SESION)

    borrarSesionPersistidaSiFamilia('familia-1')

    expect(datos.has(CLAVE_SESION_PERSISTIDA)).toBe(false)
  })

  it('borrado por familia: NO purga la sesión de OTRA familia (otra cuenta ocupó el slot)', () => {
    const { almacen, datos } = localStorageFalso()
    vi.stubGlobal('localStorage', almacen)
    guardarSesionPersistida({ ...SESION, familia: 'familia-ajena' })

    borrarSesionPersistidaSiFamilia('familia-1')

    expect(datos.get(CLAVE_SESION_PERSISTIDA)).toContain('familia-ajena')
  })

  it('shape inválido (falta un campo, no es string, o marcador no-string) → null y purga la clave', () => {
    for (const basura of [
      JSON.stringify({ refreshToken: 'solo-uno' }),
      JSON.stringify({ refreshToken: 1, refreshExpiraEn: '2026-07-17T00:00:00Z', familia: 'f' }),
      JSON.stringify({ refreshExpiraEn: '2026-07-17T00:00:00Z', familia: 'f' }),
      // Blob legacy de PR#227, de antes del campo familia: se purga (un
      // re-login único) en vez de inventarle una familia — dos pestañas
      // inventando familias distintas para la MISMA cadena se matarían entre sí.
      JSON.stringify({ refreshToken: 'legacy', refreshExpiraEn: '2026-07-17T00:00:00Z' }),
      JSON.stringify({ ...SESION, familia: 42 }),
      JSON.stringify({ ...SESION, enVuelo: 42 }),
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

  describe('generarFamilia', () => {
    it('genera identificadores no vacíos y distintos entre llamadas', () => {
      const a = generarFamilia()
      const b = generarFamilia()

      expect(a).toBeTruthy()
      expect(b).toBeTruthy()
      expect(a).not.toBe(b)
    })

    it('funciona sin crypto.randomUUID (orígenes http: emulador Android en dev)', () => {
      // randomUUID solo existe en secure contexts; getRandomValues sí está.
      const cryptoReal = globalThis.crypto
      vi.stubGlobal('crypto', {
        getRandomValues: (arr: Uint8Array<ArrayBuffer>) => cryptoReal.getRandomValues(arr),
      })

      const a = generarFamilia()
      const b = generarFamilia()

      expect(a).toBeTruthy()
      expect(a).not.toBe(b)
    })

    it('funciona incluso sin crypto (último recurso: no es un credencial, solo un discriminador)', () => {
      vi.stubGlobal('crypto', undefined)

      const a = generarFamilia()
      const b = generarFamilia()

      expect(a).toBeTruthy()
      expect(a).not.toBe(b)
    })
  })

  describe('almacenamientoFunciona', () => {
    it('true con un storage sano, y no deja rastro de la sonda', () => {
      const { almacen, datos } = localStorageFalso()
      vi.stubGlobal('localStorage', almacen)

      expect(almacenamientoFunciona()).toBe(true)
      expect(datos.has(CLAVE_SONDA_ALMACENAMIENTO)).toBe(false)
    })

    it('la sonda usa su clave dedicada: JAMÁS toca la clave real de la sesión', () => {
      const { almacen, datos } = localStorageFalso()
      vi.stubGlobal('localStorage', almacen)
      guardarSesionPersistida(SESION)

      almacenamientoFunciona()

      expect(leerSesionPersistida()).toEqual(SESION)
      expect([...datos.keys()]).toEqual([CLAVE_SESION_PERSISTIDA])
    })

    it('false si el storage lanza al escribir (modo privado estricto, cuota agotada)', () => {
      vi.stubGlobal('localStorage', {
        getItem: () => null,
        setItem: () => {
          throw new Error('QuotaExceededError')
        },
        removeItem: () => {},
      })

      expect(almacenamientoFunciona()).toBe(false)
    })

    it('false sin localStorage', () => {
      vi.stubGlobal('localStorage', undefined)

      expect(almacenamientoFunciona()).toBe(false)
    })
  })
})
