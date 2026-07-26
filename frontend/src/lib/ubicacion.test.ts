import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Geolocation } from '@capacitor/geolocation'
import {
  avisoPermisoCaducado,
  capturaPosicion,
  permisoUbicacion,
  pideUbicacion,
  registraIntentoUbicacion,
} from './ubicacion'

// El plugin habla con código nativo: en tests se mockea entero (mismo patrón que notificaciones.test.ts).
vi.mock('@capacitor/geolocation', () => ({
  Geolocation: {
    checkPermissions: vi.fn(),
    requestPermissions: vi.fn(),
    getCurrentPosition: vi.fn(),
  },
}))

const pluginMock = vi.mocked(Geolocation)

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

describe('permisoUbicacion', () => {
  it('traduce granted a concedido', async () => {
    pluginMock.checkPermissions.mockResolvedValue({ location: 'granted', coarseLocation: 'granted' })

    await expect(permisoUbicacion()).resolves.toBe('concedido')
  })

  it('traduce prompt a no_pedido', async () => {
    pluginMock.checkPermissions.mockResolvedValue({ location: 'prompt', coarseLocation: 'prompt' })

    await expect(permisoUbicacion()).resolves.toBe('no_pedido')
  })

  it('traduce prompt-with-rationale a denegado (se puede volver a pedir)', async () => {
    pluginMock.checkPermissions.mockResolvedValue({
      location: 'prompt-with-rationale',
      coarseLocation: 'prompt-with-rationale',
    })

    await expect(permisoUbicacion()).resolves.toBe('denegado')
  })

  it('traduce denied a denegado_permanente (Android no reabre el diálogo)', async () => {
    pluginMock.checkPermissions.mockResolvedValue({ location: 'denied', coarseLocation: 'denied' })

    await expect(permisoUbicacion()).resolves.toBe('denegado_permanente')
  })

  it('mira SIEMPRE el alias coarseLocation, nunca el alias location (D2: solo aproximada)', async () => {
    // location podría ir por delante (p. ej. si algún día otra parte del
    // sistema pidiera FINE): esta feature nunca debe fijarse en ese alias.
    pluginMock.checkPermissions.mockResolvedValue({ location: 'granted', coarseLocation: 'prompt' })

    await expect(permisoUbicacion()).resolves.toBe('no_pedido')
  })
})

describe('pideUbicacion', () => {
  it('pide EXCLUSIVAMENTE el permiso de ubicación aproximada, nunca fina', async () => {
    pluginMock.requestPermissions.mockResolvedValue({ location: 'granted', coarseLocation: 'granted' })

    await pideUbicacion()

    expect(pluginMock.requestPermissions).toHaveBeenCalledExactlyOnceWith({
      permissions: ['coarseLocation'],
    })
  })

  it('devuelve el estado traducido tras pedirlo', async () => {
    pluginMock.requestPermissions.mockResolvedValue({ location: 'denied', coarseLocation: 'denied' })

    await expect(pideUbicacion()).resolves.toBe('denegado_permanente')
  })
})

describe('capturaPosicion', () => {
  beforeEach(() => {
    // Camino feliz por defecto: permiso ya concedido. Los tests del guard de
    // permiso lo re-stubbean.
    pluginMock.checkPermissions.mockResolvedValue({ location: 'granted', coarseLocation: 'granted' })
  })

  it('pide precisión aproximada (enableHighAccuracy false), timeout 6 s y caché de hasta 2 min', async () => {
    pluginMock.getCurrentPosition.mockResolvedValue({
      timestamp: Date.now(),
      coords: { latitude: 43.36, longitude: -5.84, accuracy: 80 },
    } as never)

    await capturaPosicion()

    expect(pluginMock.getCurrentPosition).toHaveBeenCalledExactlyOnceWith({
      enableHighAccuracy: false,
      timeout: 6000,
      maximumAge: 120000,
    })
  })

  it('devuelve la posición con la precisión redondeada a metros enteros', async () => {
    pluginMock.getCurrentPosition.mockResolvedValue({
      timestamp: Date.now(),
      coords: { latitude: 43.36, longitude: -5.84, accuracy: 79.6 },
    } as never)

    await expect(capturaPosicion()).resolves.toEqual({
      latitud: 43.36,
      longitud: -5.84,
      precisionMetros: 80,
    })
  })

  it('sin fix (timeout o cualquier error del plugin) devuelve null, nunca lanza: es el caso normal', async () => {
    pluginMock.getCurrentPosition.mockRejectedValue(new Error('timeout'))

    await expect(capturaPosicion()).resolves.toBeNull()
  })

  it('precisión absurda (más de 5000 m) se descarta en el cliente', async () => {
    pluginMock.getCurrentPosition.mockResolvedValue({
      timestamp: Date.now(),
      coords: { latitude: 43.36, longitude: -5.84, accuracy: 5001 },
    } as never)

    await expect(capturaPosicion()).resolves.toBeNull()
  })

  it('una precisión de exactamente 5000 m SÍ se manda (el límite es inclusivo)', async () => {
    pluginMock.getCurrentPosition.mockResolvedValue({
      timestamp: Date.now(),
      coords: { latitude: 43.36, longitude: -5.84, accuracy: 5000 },
    } as never)

    await expect(capturaPosicion()).resolves.not.toBeNull()
  })

  it('sin permiso concedido, NO llama al plugin (evita el nag de Android al fichar, HIGH del review)', async () => {
    // getCurrentPosition del plugin nativo pide el permiso él solo si no está
    // concedido (GeolocationPlugin.kt): eso abriría el diálogo del sistema
    // justo después de "Salgo ahora". El guard tiene que cortar ANTES.
    pluginMock.checkPermissions.mockResolvedValue({ location: 'prompt', coarseLocation: 'prompt' })

    await expect(capturaPosicion()).resolves.toBeNull()

    expect(pluginMock.getCurrentPosition).not.toHaveBeenCalled()
  })

  it('con el permiso denegado para siempre, tampoco llama al plugin', async () => {
    pluginMock.checkPermissions.mockResolvedValue({ location: 'denied', coarseLocation: 'denied' })

    await expect(capturaPosicion()).resolves.toBeNull()

    expect(pluginMock.getCurrentPosition).not.toHaveBeenCalled()
  })
})

describe('registraIntentoUbicacion / avisoPermisoCaducado', () => {
  it('sin intentos registrados no hay aviso', () => {
    expect(avisoPermisoCaducado()).toBe(false)
  })

  it('tras 3 fichajes seguidos sin permiso efectivo, hay aviso', () => {
    registraIntentoUbicacion(false)
    registraIntentoUbicacion(false)
    registraIntentoUbicacion(false)

    expect(avisoPermisoCaducado()).toBe(true)
  })

  it('con solo 2 fichajes seguidos sin permiso efectivo, todavía no hay aviso (nunca nag al fichar)', () => {
    registraIntentoUbicacion(false)
    registraIntentoUbicacion(false)

    expect(avisoPermisoCaducado()).toBe(false)
  })

  it('un intento efectivo entre medias reinicia la cuenta', () => {
    registraIntentoUbicacion(false)
    registraIntentoUbicacion(false)
    registraIntentoUbicacion(true)
    registraIntentoUbicacion(false)
    registraIntentoUbicacion(false)

    expect(avisoPermisoCaducado()).toBe(false)
  })
})
