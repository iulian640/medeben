import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import PanelRecordatorio from './PanelRecordatorio.vue'
import { CLAVE_HORA_RECORDATORIO, DIAS_PROGRAMADOS, idsRecordatorio } from '../lib/recordatorio'

// El plugin nativo y la detección de plataforma se mockean enteros.
vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: vi.fn() },
}))
vi.mock('../lib/notificaciones', () => ({
  solicitarPermisoNotificaciones: vi.fn(),
  programarNotificaciones: vi.fn(),
  cancelarNotificaciones: vi.fn(),
}))

import { Capacitor } from '@capacitor/core'
import {
  cancelarNotificaciones,
  programarNotificaciones,
  solicitarPermisoNotificaciones,
} from '../lib/notificaciones'

// Mismo idioma que libreta.test.ts: localStorage no existe en el entorno de
// test, se stubea con un Map.
const datos = new Map<string, string>()

beforeEach(() => {
  vi.clearAllMocks()
  datos.clear()
  vi.stubGlobal('localStorage', {
    getItem: (clave: string) => datos.get(clave) ?? null,
    setItem: (clave: string, valor: string) => void datos.set(clave, valor),
    removeItem: (clave: string) => void datos.delete(clave),
  })
  vi.mocked(Capacitor.isNativePlatform).mockReturnValue(true)
  vi.mocked(programarNotificaciones).mockResolvedValue()
  vi.mocked(cancelarNotificaciones).mockResolvedValue()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

async function montar() {
  const wrapper = mount(PanelRecordatorio)
  await flushPromises()
  return wrapper
}

describe('PanelRecordatorio', () => {
  it('en el navegador (no nativo) no se pinta nada', async () => {
    vi.mocked(Capacitor.isNativePlatform).mockReturnValue(false)

    const wrapper = await montar()

    expect(wrapper.find('.recordatorio').exists()).toBe(false)
  })

  it('activar pide permiso, programa la quincena y guarda la hora', async () => {
    vi.mocked(solicitarPermisoNotificaciones).mockResolvedValue(true)
    const wrapper = await montar()

    await wrapper.find('input[type="checkbox"]').setValue(true)
    await flushPromises()

    expect(solicitarPermisoNotificaciones).toHaveBeenCalledOnce()
    const plan = vi.mocked(programarNotificaciones).mock.calls[0][0]
    expect(plan).toHaveLength(DIAS_PROGRAMADOS)
    expect(datos.get(CLAVE_HORA_RECORDATORIO)).toBe('21:30')
    expect(wrapper.find('input[type="time"]').exists()).toBe(true)
  })

  it('sin permiso: aviso honesto, nada programado, preferencia sin guardar', async () => {
    vi.mocked(solicitarPermisoNotificaciones).mockResolvedValue(false)
    const wrapper = await montar()

    await wrapper.find('input[type="checkbox"]').setValue(true)
    await flushPromises()

    expect(programarNotificaciones).not.toHaveBeenCalled()
    expect(datos.has(CLAVE_HORA_RECORDATORIO)).toBe(false)
    expect(wrapper.find('.nota').text()).toContain('Sin permiso')
  })

  it('desactivar cancela el bloque de ids y borra la preferencia', async () => {
    vi.mocked(solicitarPermisoNotificaciones).mockResolvedValue(true)
    const wrapper = await montar()
    await wrapper.find('input[type="checkbox"]').setValue(true)
    await flushPromises()

    await wrapper.find('input[type="checkbox"]').setValue(false)
    await flushPromises()

    expect(cancelarNotificaciones).toHaveBeenCalledWith(idsRecordatorio())
    expect(datos.has(CLAVE_HORA_RECORDATORIO)).toBe(false)
  })

  it('con hora guardada, al montar renueva la quincena en silencio', async () => {
    datos.set(CLAVE_HORA_RECORDATORIO, '09:15')

    const wrapper = await montar()

    expect(programarNotificaciones).toHaveBeenCalledOnce()
    expect((wrapper.find('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.find('input[type="time"]').element as HTMLInputElement).value).toBe('09:15')
  })

  it('cambiar la hora con el recordatorio activo reprograma con la nueva', async () => {
    vi.mocked(solicitarPermisoNotificaciones).mockResolvedValue(true)
    const wrapper = await montar()
    await wrapper.find('input[type="checkbox"]').setValue(true)
    await flushPromises()
    vi.mocked(programarNotificaciones).mockClear()

    await wrapper.find('input[type="time"]').setValue('07:45')
    await flushPromises()

    expect(datos.get(CLAVE_HORA_RECORDATORIO)).toBe('07:45')
    const plan = vi.mocked(programarNotificaciones).mock.calls[0][0]
    expect(plan[0].fecha.getHours()).toBe(7)
    expect(plan[0].fecha.getMinutes()).toBe(45)
  })
})
