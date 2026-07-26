import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Posicion } from '../lib/ubicacion'

vi.mock('./api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  ApiError: class ApiError extends Error {
    status: number
    constructor(status: number, message: string) {
      super(message)
      this.status = status
    }
  },
}))

vi.mock('../lib/ubicacion', () => ({
  capturaPosicion: vi.fn(),
  registraIntentoUbicacion: vi.fn(),
}))

import { api, ApiError } from './api'
import { capturaPosicion, registraIntentoUbicacion } from '../lib/ubicacion'
import {
  VERSION_CONSENTIMIENTO_UBICACION,
  anotaUbicacion,
  avisoConsentimientoCaducado,
  deleteConsentimientoUbicacion,
  deleteUbicaciones,
  getCentroTrabajo,
  limpiaAvisoConsentimientoCaducado,
  marcaUbicacionActivada,
  marcaUbicacionDesactivada,
  postConsentimientoUbicacion,
  putCentroTrabajo,
  ubicacionActivada,
} from './ubicacion'

// Mismo idioma que recordatorio.test.ts: localStorage no existe en el
// entorno de test, se stubea con un Map.
const datos = new Map<string, string>()

beforeEach(() => {
  vi.clearAllMocks()
  datos.clear()
  vi.stubGlobal('localStorage', {
    getItem: (clave: string) => datos.get(clave) ?? null,
    setItem: (clave: string, valor: string) => void datos.set(clave, valor),
    removeItem: (clave: string) => void datos.delete(clave),
  })
})

const POSICION: Posicion = { latitud: 43.36, longitud: -5.84, precisionMetros: 80 }

describe('consentimiento', () => {
  it('postConsentimientoUbicacion manda la versión canónica del texto', async () => {
    await postConsentimientoUbicacion()

    expect(api.post).toHaveBeenCalledExactlyOnceWith('/ubicacion/consentimiento', {
      versionTexto: VERSION_CONSENTIMIENTO_UBICACION,
    })
  })

  it('deleteConsentimientoUbicacion revoca sin borrar histórico', async () => {
    await deleteConsentimientoUbicacion()

    expect(api.delete).toHaveBeenCalledExactlyOnceWith('/ubicacion/consentimiento')
  })
})

describe('centro de trabajo', () => {
  it('getCentroTrabajo pide el centro vigente', async () => {
    await getCentroTrabajo()

    expect(api.get).toHaveBeenCalledExactlyOnceWith('/centro-trabajo')
  })

  it('putCentroTrabajo manda la posición capturada y el alias', async () => {
    await putCentroTrabajo(POSICION, 'El bar')

    expect(api.put).toHaveBeenCalledExactlyOnceWith('/centro-trabajo', {
      latitud: 43.36,
      longitud: -5.84,
      alias: 'El bar',
    })
  })

  it('putCentroTrabajo sin alias manda null (es opcional, no obligatorio)', async () => {
    await putCentroTrabajo(POSICION)

    expect(api.put).toHaveBeenCalledExactlyOnceWith('/centro-trabajo', {
      latitud: 43.36,
      longitud: -5.84,
      alias: null,
    })
  })
})

describe('borrado de histórico', () => {
  it('deleteUbicaciones borra TODO el histórico del usuario', async () => {
    await deleteUbicaciones()

    expect(api.delete).toHaveBeenCalledExactlyOnceWith('/ubicaciones')
  })
})

describe('estado local de activación', () => {
  it('no está activada por defecto', () => {
    expect(ubicacionActivada()).toBe(false)
  })

  it('marcaUbicacionActivada la enciende; marcaUbicacionDesactivada la apaga', () => {
    marcaUbicacionActivada()
    expect(ubicacionActivada()).toBe(true)

    marcaUbicacionDesactivada()
    expect(ubicacionActivada()).toBe(false)
  })
})

describe('anotaUbicacion', () => {
  it('con la feature apagada no captura nada ni llama al endpoint', async () => {
    await anotaUbicacion('apunte-1')

    expect(capturaPosicion).not.toHaveBeenCalled()
    expect(api.post).not.toHaveBeenCalled()
  })

  it('sin fix, no llama al endpoint de adjuntar y registra el intento como fallido', async () => {
    marcaUbicacionActivada()
    vi.mocked(capturaPosicion).mockResolvedValue(null)

    await anotaUbicacion('apunte-1')

    expect(api.post).not.toHaveBeenCalled()
    expect(registraIntentoUbicacion).toHaveBeenCalledExactlyOnceWith(false)
  })

  it('con fix, adjunta la posición al apunte por POST (no PUT) y registra el intento como efectivo', async () => {
    marcaUbicacionActivada()
    vi.mocked(capturaPosicion).mockResolvedValue(POSICION)
    vi.mocked(api.post).mockResolvedValue({
      veredicto: 'DENTRO',
      distanciaMetros: 20,
      precisionMetros: 80,
      registradaEn: '2026-07-26T09:02:00+02:00',
    })

    await anotaUbicacion('apunte-1')

    expect(api.post).toHaveBeenCalledExactlyOnceWith('/fichajes/apunte-1/ubicacion', {
      latitud: 43.36,
      longitud: -5.84,
      precisionMetros: 80,
    })
    expect(registraIntentoUbicacion).toHaveBeenCalledExactlyOnceWith(true)
  })

  it('un 403 del servidor (consentimiento ya no vigente) apaga la feature en local y avisa una vez', async () => {
    marcaUbicacionActivada()
    vi.mocked(capturaPosicion).mockResolvedValue(POSICION)
    vi.mocked(api.post).mockRejectedValue(new ApiError(403, 'sin consentimiento vigente'))

    await anotaUbicacion('apunte-1')

    expect(ubicacionActivada()).toBe(false)
    expect(avisoConsentimientoCaducado()).toBe(true)
  })

  it('nunca lanza: un fallo cualquiera (red, 409, 422) deja el fichaje intacto y en silencio', async () => {
    marcaUbicacionActivada()
    vi.mocked(capturaPosicion).mockResolvedValue(POSICION)
    vi.mocked(api.post).mockRejectedValue(new ApiError(409, 'ya existe'))

    await expect(anotaUbicacion('apunte-1')).resolves.toBeUndefined()
    // Un 409 no es un problema de consentimiento: la feature sigue activa.
    expect(ubicacionActivada()).toBe(true)
    expect(avisoConsentimientoCaducado()).toBe(false)
  })

  it('limpiaAvisoConsentimientoCaducado retira el aviso una vez leído', () => {
    marcaUbicacionActivada()
    vi.mocked(capturaPosicion).mockResolvedValue(null)
    datos.set('medeben.ubicacion.aviso-consentimiento-caducado', '1')

    limpiaAvisoConsentimientoCaducado()

    expect(avisoConsentimientoCaducado()).toBe(false)
  })
})
