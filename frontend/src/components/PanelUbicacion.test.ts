import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import PanelUbicacion from './PanelUbicacion.vue'

// El plugin nativo y la detección de plataforma se mockean enteros (mismo
// patrón que PanelRecordatorio.test.ts).
vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: vi.fn() },
}))
vi.mock('../lib/ubicacion', () => ({
  pideUbicacion: vi.fn(),
  capturaPosicion: vi.fn(),
  avisoPermisoCaducado: vi.fn().mockReturnValue(false),
}))
vi.mock('../services/ubicacion', () => ({
  VERSION_CONSENTIMIENTO_UBICACION: '1.0',
  postConsentimientoUbicacion: vi.fn(),
  deleteConsentimientoUbicacion: vi.fn(),
  getCentroTrabajo: vi.fn().mockRejectedValue(new Error('sin centro')),
  putCentroTrabajo: vi.fn(),
  deleteUbicaciones: vi.fn(),
  ubicacionActivada: vi.fn().mockReturnValue(false),
  marcaUbicacionActivada: vi.fn(),
  marcaUbicacionDesactivada: vi.fn(),
  avisoConsentimientoCaducado: vi.fn().mockReturnValue(false),
  limpiaAvisoConsentimientoCaducado: vi.fn(),
}))

import { Capacitor } from '@capacitor/core'
import { avisoPermisoCaducado, capturaPosicion, pideUbicacion } from '../lib/ubicacion'
import {
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
} from '../services/ubicacion'

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/privacidad', name: 'privacidad', component: { template: '<div />' } },
    ],
  })
}

async function montar() {
  const router = crearRouter()
  await router.push('/')
  const wrapper = mount(PanelUbicacion, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

const POSICION = { latitud: 43.36, longitud: -5.84, precisionMetros: 65 }

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(Capacitor.isNativePlatform).mockReturnValue(true)
  vi.mocked(ubicacionActivada).mockReturnValue(false)
  vi.mocked(avisoConsentimientoCaducado).mockReturnValue(false)
  vi.mocked(avisoPermisoCaducado).mockReturnValue(false)
  vi.mocked(getCentroTrabajo).mockRejectedValue(new Error('sin centro'))
})

function boton(wrapper: Awaited<ReturnType<typeof montar>>, texto: string) {
  const encontrado = wrapper.findAll('button').find((b) => b.text().includes(texto))
  if (!encontrado) {
    throw new Error(`No hay botón que contenga "${texto}"`)
  }
  return encontrado
}

describe('PanelUbicacion — visibilidad', () => {
  it('en el navegador (no nativo) no se pinta nada', async () => {
    vi.mocked(Capacitor.isNativePlatform).mockReturnValue(false)

    const wrapper = await montar()

    expect(wrapper.find('.ubicacion').exists()).toBe(false)
  })

  it('apagado por defecto: ningún método del plugin se llama al montar', async () => {
    const wrapper = await montar()

    expect(
      (wrapper.find('input[type="checkbox"]').element as HTMLInputElement).checked,
    ).toBe(false)
    expect(pideUbicacion).not.toHaveBeenCalled()
    expect(capturaPosicion).not.toHaveBeenCalled()
  })
})

describe('PanelUbicacion — pantalla explicativa (checklist de 7 puntos)', () => {
  it('activar el interruptor abre la explicación ANTES de pedir nada al sistema', async () => {
    const wrapper = await montar()

    await wrapper.find('input[type="checkbox"]').setValue(true)

    expect(pideUbicacion).not.toHaveBeenCalled()
    expect(postConsentimientoUbicacion).not.toHaveBeenCalled()
    expect(wrapper.text()).toMatch(/finalidad/i)
  })

  it('contiene los 7 puntos exigidos por el contrato', async () => {
    const wrapper = await montar()
    await wrapper.find('input[type="checkbox"]').setValue(true)

    const texto = wrapper.text().toLowerCase()

    // 1. Finalidad
    expect(texto).toMatch(/finalidad/)
    // 2. Base 6.1.a, "inequívoco y expreso" (NUNCA "explícito", corrección del contrato)
    expect(texto).toMatch(/6\.1\.a/)
    expect(texto).toMatch(/inequívoco y expreso/)
    // 3. Qué se envía y a dónde: sale del dispositivo (prominent disclosure de Play)
    expect(texto).toMatch(/sale de tu dispositivo/)
    // 4. Conservación 15 meses salvo reclamación en curso
    expect(texto).toMatch(/15 meses/)
    expect(texto).toMatch(/reclamación en curso/)
    // 5. Revocación en un toque
    expect(texto).toMatch(/revocar.*en un toque|un toque.*revocar/)
    // 6. Borrado del histórico en un toque
    expect(texto).toMatch(/borrar.*histórico|histórico.*borrar/)
    // 7. Derechos y AEPD
    expect(texto).toMatch(/aepd/)
  })

  it('el aviso anti-coacción está presente', async () => {
    const wrapper = await montar()
    await wrapper.find('input[type="checkbox"]').setValue(true)

    expect(wrapper.text().toLowerCase()).toContain('tu empresa no puede exigirte')
    expect(wrapper.text()).toContain('art. 90')
  })

  it('enlaza a la política completa', async () => {
    const wrapper = await montar()
    await wrapper.find('input[type="checkbox"]').setValue(true)

    const enlace = wrapper.find('a[href="/privacidad"]')
    expect(enlace.exists()).toBe(true)
  })

  it('la casilla de consentimiento NO viene premarcada', async () => {
    const wrapper = await montar()
    await wrapper.find('input[type="checkbox"]').setValue(true)

    const casilla = wrapper.find<HTMLInputElement>('.casilla-consentimiento input[type="checkbox"]')
    expect(casilla.element.checked).toBe(false)
  })

  it('el botón de continuar está deshabilitado hasta marcar la casilla', async () => {
    const wrapper = await montar()
    await wrapper.find('input[type="checkbox"]').setValue(true)

    expect(boton(wrapper, 'Aceptar y continuar').attributes('disabled')).toBeDefined()

    await wrapper.find('.casilla-consentimiento input[type="checkbox"]').setValue(true)

    expect(boton(wrapper, 'Aceptar y continuar').attributes('disabled')).toBeUndefined()
  })
})

describe('PanelUbicacion — activación completa', () => {
  async function abreYAcepta(wrapper: Awaited<ReturnType<typeof montar>>) {
    await wrapper.find('input[type="checkbox"]').setValue(true)
    await wrapper.find('.casilla-consentimiento input[type="checkbox"]').setValue(true)
    await boton(wrapper, 'Aceptar y continuar').trigger('click')
    await flushPromises()
  }

  it('si el POST de consentimiento falla, la feature NO se activa', async () => {
    vi.mocked(postConsentimientoUbicacion).mockRejectedValue(new Error('caído'))
    const wrapper = await montar()

    await abreYAcepta(wrapper)

    expect(pideUbicacion).not.toHaveBeenCalled()
    expect(marcaUbicacionActivada).not.toHaveBeenCalled()
  })

  it('con el consentimiento OK, pide el permiso de Android aquí (no antes)', async () => {
    vi.mocked(postConsentimientoUbicacion).mockResolvedValue(undefined)
    vi.mocked(pideUbicacion).mockResolvedValue('concedido')
    const wrapper = await montar()

    await abreYAcepta(wrapper)

    expect(postConsentimientoUbicacion).toHaveBeenCalledOnce()
    expect(pideUbicacion).toHaveBeenCalledOnce()
  })

  it('si el permiso se deniega, no se activa y se avisa sin insistir', async () => {
    vi.mocked(postConsentimientoUbicacion).mockResolvedValue(undefined)
    vi.mocked(pideUbicacion).mockResolvedValue('denegado_permanente')
    const wrapper = await montar()

    await abreYAcepta(wrapper)

    expect(marcaUbicacionActivada).not.toHaveBeenCalled()
    expect(wrapper.text().toLowerCase()).toContain('bloqueado')
  })

  it('con permiso concedido, ofrece marcar el centro; al marcarlo declara la fecha y la precisión', async () => {
    vi.mocked(postConsentimientoUbicacion).mockResolvedValue(undefined)
    vi.mocked(pideUbicacion).mockResolvedValue('concedido')
    vi.mocked(capturaPosicion).mockResolvedValue(POSICION)
    vi.mocked(putCentroTrabajo).mockResolvedValue({
      alias: null,
      radioMetros: 150,
      declaradoEn: '2026-07-26T09:00:00+02:00',
    })
    const wrapper = await montar()
    await abreYAcepta(wrapper)

    await boton(wrapper, 'Estoy en el trabajo').trigger('click')
    await flushPromises()

    expect(putCentroTrabajo).toHaveBeenCalledWith(POSICION)
    expect(marcaUbicacionActivada).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('Declarado el 26/07/2026')
    expect(wrapper.text()).toContain('65')
  })

  it('sin centro definido (sin fix al marcarlo), la captura no se activa', async () => {
    vi.mocked(postConsentimientoUbicacion).mockResolvedValue(undefined)
    vi.mocked(pideUbicacion).mockResolvedValue('concedido')
    vi.mocked(capturaPosicion).mockResolvedValue(null)
    const wrapper = await montar()
    await abreYAcepta(wrapper)

    await boton(wrapper, 'Estoy en el trabajo').trigger('click')
    await flushPromises()

    expect(putCentroTrabajo).not.toHaveBeenCalled()
    expect(marcaUbicacionActivada).not.toHaveBeenCalled()
  })
})

describe('PanelUbicacion — ya activada', () => {
  beforeEach(() => {
    vi.mocked(ubicacionActivada).mockReturnValue(true)
  })

  it('muestra el interruptor encendido, borrar histórico y desactivar', async () => {
    const wrapper = await montar()

    expect(
      (wrapper.find('input[type="checkbox"]').element as HTMLInputElement).checked,
    ).toBe(true)
    expect(() => boton(wrapper, 'Borrar mi histórico')).not.toThrow()
  })

  it('borrar histórico llama a deleteUbicaciones', async () => {
    vi.mocked(deleteUbicaciones).mockResolvedValue(undefined)
    const wrapper = await montar()

    await boton(wrapper, 'Borrar mi histórico').trigger('click')
    await flushPromises()

    expect(deleteUbicaciones).toHaveBeenCalledOnce()
  })

  it('si el borrado del histórico falla, se avisa en pantalla en vez de fallar en silencio (HIGH del review)', async () => {
    vi.mocked(deleteUbicaciones).mockRejectedValue(new Error('sin red'))
    const wrapper = await montar()

    await boton(wrapper, 'Borrar mi histórico').trigger('click')
    await flushPromises()

    expect(wrapper.text().toLowerCase()).toContain('no se ha podido borrar')
  })

  it('apagar el interruptor revoca el consentimiento y desactiva en local', async () => {
    vi.mocked(deleteConsentimientoUbicacion).mockResolvedValue(undefined)
    const wrapper = await montar()

    await wrapper.find('input[type="checkbox"]').setValue(false)
    await flushPromises()

    expect(deleteConsentimientoUbicacion).toHaveBeenCalledOnce()
    expect(marcaUbicacionDesactivada).toHaveBeenCalledOnce()
  })

  it('si el DELETE de revocación falla, NO se da por revocado: sigue activo y avisa (HIGH del review)', async () => {
    vi.mocked(deleteConsentimientoUbicacion).mockRejectedValue(new Error('sin red'))
    const wrapper = await montar()

    await wrapper.find('input[type="checkbox"]').setValue(false)
    await flushPromises()

    expect(marcaUbicacionDesactivada).not.toHaveBeenCalled()
    expect(
      (wrapper.find('input[type="checkbox"]').element as HTMLInputElement).checked,
    ).toBe(true)
    expect(wrapper.text().toLowerCase()).toContain('no se ha podido')
  })

  it('al montar con la feature ya activa, repuebla la fecha de declaración del centro', async () => {
    vi.mocked(getCentroTrabajo).mockResolvedValue({
      alias: null,
      radioMetros: 150,
      declaradoEn: '2026-06-01T09:00:00+02:00',
    })

    const wrapper = await montar()

    expect(getCentroTrabajo).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('Declarado el 01/06/2026')
  })

  it('tras 3 fichajes seguidos sin permiso efectivo, aviso discreto (nunca al fichar)', async () => {
    vi.mocked(avisoPermisoCaducado).mockReturnValue(true)

    const wrapper = await montar()

    expect(wrapper.text().toLowerCase()).toContain('permiso')
  })
})

describe('PanelUbicacion — consentimiento caducado en servidor (403)', () => {
  it('avisa una vez y se puede descartar', async () => {
    vi.mocked(avisoConsentimientoCaducado).mockReturnValue(true)
    const wrapper = await montar()

    expect(wrapper.text().toLowerCase()).toContain('consentimiento')

    await boton(wrapper, 'Entendido').trigger('click')

    expect(limpiaAvisoConsentimientoCaducado).toHaveBeenCalledOnce()
  })
})
