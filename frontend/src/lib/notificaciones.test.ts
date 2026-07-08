import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LocalNotifications } from '@capacitor/local-notifications'
import { programarNotificacion, solicitarPermisoNotificaciones } from './notificaciones'

// El plugin habla con código nativo: en tests se mockea entero.
vi.mock('@capacitor/local-notifications', () => ({
  LocalNotifications: {
    checkPermissions: vi.fn(),
    requestPermissions: vi.fn(),
    schedule: vi.fn(),
  },
}))

const pluginMock = vi.mocked(LocalNotifications)

beforeEach(() => {
  vi.clearAllMocks()
})

describe('solicitarPermisoNotificaciones', () => {
  it('devuelve true sin volver a preguntar si el permiso ya está concedido', async () => {
    pluginMock.checkPermissions.mockResolvedValue({ display: 'granted' })

    await expect(solicitarPermisoNotificaciones()).resolves.toBe(true)
    expect(pluginMock.requestPermissions).not.toHaveBeenCalled()
  })

  it('pide el permiso si aún no está concedido y devuelve true si el usuario acepta', async () => {
    pluginMock.checkPermissions.mockResolvedValue({ display: 'prompt' })
    pluginMock.requestPermissions.mockResolvedValue({ display: 'granted' })

    await expect(solicitarPermisoNotificaciones()).resolves.toBe(true)
    expect(pluginMock.requestPermissions).toHaveBeenCalledOnce()
  })

  it('devuelve false si el usuario deniega el permiso', async () => {
    pluginMock.checkPermissions.mockResolvedValue({ display: 'prompt' })
    pluginMock.requestPermissions.mockResolvedValue({ display: 'denied' })

    await expect(solicitarPermisoNotificaciones()).resolves.toBe(false)
  })

  it('devuelve false si el permiso ya estaba denegado (Android no reabre el diálogo)', async () => {
    pluginMock.checkPermissions.mockResolvedValue({ display: 'denied' })
    pluginMock.requestPermissions.mockResolvedValue({ display: 'denied' })

    await expect(solicitarPermisoNotificaciones()).resolves.toBe(false)
  })
})

describe('programarNotificacion', () => {
  it('traduce los campos al esquema del plugin y programa para la fecha dada', async () => {
    pluginMock.schedule.mockResolvedValue({ notifications: [] })
    const fecha = new Date('2026-07-12T21:00:00')

    await programarNotificacion({
      id: 42,
      titulo: 'El martes 12 se sella en 3 días',
      cuerpo: 'Confirma tus fichajes pendientes antes del sellado.',
      fecha,
    })

    expect(pluginMock.schedule).toHaveBeenCalledExactlyOnceWith({
      notifications: [
        {
          id: 42,
          title: 'El martes 12 se sella en 3 días',
          body: 'Confirma tus fichajes pendientes antes del sellado.',
          schedule: { at: fecha },
        },
      ],
    })
  })

  it('propaga el error del plugin: mejor fallo visible que notificación fantasma', async () => {
    pluginMock.schedule.mockRejectedValue(new Error('plugin no disponible'))

    await expect(
      programarNotificacion({ id: 1, titulo: 't', cuerpo: 'c', fecha: new Date() }),
    ).rejects.toThrow('plugin no disponible')
  })
})
