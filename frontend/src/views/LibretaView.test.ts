import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DOMWrapper, mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import type { ApunteGuardado, EstadoDiaGuardado } from '../services/fichajes'
import { CLAVE_ONBOARDING_LIBRETA } from '../lib/libreta'
import LibretaView from './LibretaView.vue'

vi.mock('../services/fichajes', () => ({
  getEstadoDia: vi.fn(),
  postApunte: vi.fn(),
}))
vi.mock('../services/horario', () => ({
  getHorarioSemana: vi.fn(),
}))

import { getEstadoDia, postApunte } from '../services/fichajes'

const apunteEntrada: ApunteGuardado = {
  fecha: '2026-07-08',
  tipo: 'ENTRADA',
  hora: '14:05',
  motivo: null,
  origen: 'CONFIRMADO',
  registradoEn: new Date(2026, 6, 8, 14, 6).toISOString(),
}

const diaServidor: EstadoDiaGuardado = {
  fecha: '2026-07-08',
  estado: 'EN_CURSO',
  sellado: false,
  selladoDesde: '2026-07-23',
  minutosTrabajados: null,
  apuntes: [apunteEntrada],
}

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/libreta', name: 'libreta', component: LibretaView },
      { path: '/libreta/semana', name: 'libreta-semana', component: Stub },
    ],
  })
}

async function montar(): Promise<VueWrapper> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = crearRouter()
  await router.push('/libreta')
  const wrapper = mount(LibretaView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

function boton(wrapper: VueWrapper, texto: string) {
  const encontrado = wrapper.findAll('button').find((b) => b.text() === texto)
  if (!encontrado) {
    throw new Error(`No hay botón "${texto}"`)
  }
  return encontrado
}

/**
 * Los paneles de hora manual y ausencia viven siempre en el DOM (PanelPlegable
 * los despliega animado en vez de montarlos con v-if), así que ahora hay dos
 * <form> a la vez: localizar por el campo que contienen evita la ambigüedad
 * de "el primer form", que además dejaría de ser correcto si cambia el orden.
 */
function formularioDe(wrapper: VueWrapper, selectorCampo: string) {
  const campo = wrapper.get(selectorCampo).element
  const form = campo.closest('form')
  if (!form) {
    throw new Error(`El campo "${selectorCampo}" no está dentro de un <form>`)
  }
  return new DOMWrapper(form)
}

/** Este jsdom no trae localStorage: un doble mínimo en memoria basta. */
function localStorageFalso(): Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> {
  const datos = new Map<string, string>()
  return {
    getItem: (clave: string) => datos.get(clave) ?? null,
    setItem: (clave: string, valor: string) => void datos.set(clave, valor),
    removeItem: (clave: string) => void datos.delete(clave),
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('localStorage', localStorageFalso())
  // Por defecto el onboarding ya está visto; los tests de onboarding lo quitan.
  localStorage.setItem(CLAVE_ONBOARDING_LIBRETA, '1')
  vi.mocked(getEstadoDia).mockResolvedValue(diaServidor)
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('LibretaView — el día', () => {
  it('muestra el estado del día y sus apuntes, sin el contador de sellado (retirado por ruido)', async () => {
    const wrapper = await montar()

    expect(wrapper.text()).toContain('miércoles 08/07/2026')
    expect(wrapper.text()).toContain('En curso: falta la salida')
    expect(wrapper.text()).toContain('Entrada')
    expect(wrapper.text()).toContain('a las 14:05')
    expect(wrapper.text()).toContain('fichado al momento')
    // El contador diario de sellado se retiró: era ruido. La protección se
    // explica en el onboarding y el panel de rectificación aparece cuando toca.
    expect(wrapper.text()).not.toMatch(/se sella|queda protegido como prueba/)
  })

  it('si la carga falla, enseña el error y deja reintentar', async () => {
    vi.mocked(getEstadoDia).mockRejectedValueOnce(
      new ApiError(500, 'API 500', { status: 500, detail: 'Error interno' }),
    )

    const wrapper = await montar()

    expect(wrapper.find('[role="alert"]').text()).toContain('Error interno')

    vi.mocked(getEstadoDia).mockResolvedValue(diaServidor)
    await boton(wrapper, 'Reintentar').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('En curso: falta la salida')
  })

  it('el motivo de una ausencia se interpola como texto, nunca como HTML', async () => {
    vi.mocked(getEstadoDia).mockResolvedValue({
      ...diaServidor,
      estado: 'AUSENCIA',
      apuntes: [
        {
          ...apunteEntrada,
          tipo: 'AUSENCIA',
          hora: null,
          motivo: '<img src="x" onerror="alert(1)">',
        },
      ],
    })

    const wrapper = await montar()

    expect(wrapper.find('.apuntes img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src="x" onerror="alert(1)">')
  })
})

describe('LibretaView — fichar', () => {
  it('"Entro ahora" manda la fecha del backend y la hora del momento, y enseña el sello', async () => {
    // El navegador ya va por la madrugada del día 9, pero el estado cargado
    // dice que el día en pantalla es el 8: manda la fecha del backend.
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 6, 9, 1, 30) })
    vi.mocked(postApunte).mockResolvedValue(apunteEntrada)
    const wrapper = await montar()

    await boton(wrapper, 'Entro ahora').trigger('click')
    await flushPromises()

    expect(postApunte).toHaveBeenCalledWith({
      fecha: '2026-07-08',
      tipo: 'ENTRADA',
      hora: '01:30',
      motivo: null,
      rectificacionTardiaConfirmada: false,
    })
    expect(wrapper.text()).toContain('✓ apuntado a las 14:06')
  })

  it('"Salgo ahora" manda una SALIDA', async () => {
    vi.mocked(postApunte).mockResolvedValue({ ...apunteEntrada, tipo: 'SALIDA' })
    const wrapper = await montar()

    await boton(wrapper, 'Salgo ahora').trigger('click')
    await flushPromises()

    expect(postApunte).toHaveBeenCalledWith(expect.objectContaining({ tipo: 'SALIDA' }))
  })

  it('con hora manual manda la hora elegida', async () => {
    vi.mocked(postApunte).mockResolvedValue({ ...apunteEntrada, tipo: 'SALIDA', hora: '23:45' })
    const wrapper = await montar()

    await boton(wrapper, '¿A otra hora?').trigger('click')
    await wrapper.find('#hora-manual').setValue('23:45')
    await boton(wrapper, 'Salida a esa hora').trigger('click')
    await flushPromises()

    expect(postApunte).toHaveBeenCalledWith(
      expect.objectContaining({ tipo: 'SALIDA', hora: '23:45' }),
    )
  })

  it('en el panel de hora manual, Intro (submit del form) ficha una ENTRADA', async () => {
    vi.mocked(postApunte).mockResolvedValue({ ...apunteEntrada, hora: '09:00' })
    const wrapper = await montar()

    await boton(wrapper, '¿A otra hora?').trigger('click')
    await wrapper.find('#hora-manual').setValue('09:00')
    await formularioDe(wrapper, '#hora-manual').trigger('submit')
    await flushPromises()

    expect(postApunte).toHaveBeenCalledWith(
      expect.objectContaining({ tipo: 'ENTRADA', hora: '09:00' }),
    )
  })

  it('doble submit: con el POST en vuelo los botones quedan deshabilitados', async () => {
    let resolverPost!: (a: ApunteGuardado) => void
    vi.mocked(postApunte).mockReturnValue(
      new Promise((resolve) => {
        resolverPost = resolve
      }),
    )
    const wrapper = await montar()

    await boton(wrapper, 'Entro ahora').trigger('click')

    expect(boton(wrapper, 'Entro ahora').attributes('disabled')).toBeDefined()
    expect(boton(wrapper, 'Salgo ahora').attributes('disabled')).toBeDefined()

    await boton(wrapper, 'Salgo ahora').trigger('click')
    expect(postApunte).toHaveBeenCalledTimes(1)

    resolverPost(apunteEntrada)
    await flushPromises()
    expect(boton(wrapper, 'Entro ahora').attributes('disabled')).toBeUndefined()
  })
})

describe('LibretaView — ausencia', () => {
  it('el panel avisa de la privacidad del motivo y sin texto manda motivo null', async () => {
    vi.mocked(postApunte).mockResolvedValue({
      ...apunteEntrada,
      tipo: 'AUSENCIA',
      hora: null,
    })
    const wrapper = await montar()

    await boton(wrapper, 'No he ido').trigger('click')

    expect(wrapper.text()).toContain('El motivo es opcional; si lo escribes, queda en tu libreta.')

    // El panel es un <form>: registrar (botón submit o Intro) dispara el submit.
    await formularioDe(wrapper, '#motivo').trigger('submit')
    await flushPromises()

    expect(postApunte).toHaveBeenCalledWith({
      fecha: '2026-07-08',
      tipo: 'AUSENCIA',
      hora: null,
      motivo: null,
      rectificacionTardiaConfirmada: false,
    })
  })

  it('con motivo escrito lo manda tal cual (recortado)', async () => {
    vi.mocked(postApunte).mockResolvedValue({
      ...apunteEntrada,
      tipo: 'AUSENCIA',
      hora: null,
      motivo: 'médico',
    })
    const wrapper = await montar()

    await boton(wrapper, 'No he ido').trigger('click')
    await wrapper.find('#motivo').setValue('  médico  ')
    await formularioDe(wrapper, '#motivo').trigger('submit')
    await flushPromises()

    expect(postApunte).toHaveBeenCalledWith(expect.objectContaining({ motivo: 'médico' }))
  })
})

describe('LibretaView — día sellado (409)', () => {
  it('explica el sellado y solo reenvía como rectificación tardía tras confirmar', async () => {
    vi.mocked(postApunte).mockRejectedValueOnce(
      new ApiError(409, 'API 409', {
        status: 409,
        detail: 'El día 2026-07-08 ya quedó protegido (pasados 14 días): solo cabe una rectificación tardía',
      }),
    )
    vi.mocked(postApunte).mockResolvedValueOnce({
      ...apunteEntrada,
      origen: 'RECTIFICACION_TARDIA',
    })
    const wrapper = await montar()

    await boton(wrapper, 'Entro ahora').trigger('click')
    await flushPromises()

    // La explicación en cristiano, con la fricción del checkbox.
    expect(wrapper.text()).toContain('Este día ya quedó protegido')
    expect(wrapper.text()).toContain('rectificación tardía')
    expect(wrapper.text()).toContain('lo ya protegido no se toca')
    const confirmar = boton(wrapper, 'Registrar la rectificación')
    expect(confirmar.attributes('disabled')).toBeDefined()

    await wrapper.find('input[type="checkbox"]').setValue(true)
    expect(boton(wrapper, 'Registrar la rectificación').attributes('disabled')).toBeUndefined()

    await boton(wrapper, 'Registrar la rectificación').trigger('click')
    await flushPromises()

    expect(postApunte).toHaveBeenLastCalledWith(
      expect.objectContaining({ tipo: 'ENTRADA', rectificacionTardiaConfirmada: true }),
    )
    // Tras el éxito, el panel de rectificación desaparece.
    expect(wrapper.text()).not.toContain('Este día ya quedó protegido')
  })
})

describe('LibretaView — onboarding', () => {
  it('se muestra solo la primera vez y al cerrarlo guarda el flag', async () => {
    localStorage.removeItem(CLAVE_ONBOARDING_LIBRETA)
    const wrapper = await montar()

    expect(wrapper.text()).toContain('Fichar al momento vale más')

    await boton(wrapper, 'Siguiente').trigger('click')
    await boton(wrapper, 'Siguiente').trigger('click')
    await boton(wrapper, 'Empezar a fichar').trigger('click')

    expect(localStorage.getItem(CLAVE_ONBOARDING_LIBRETA)).toBe('1')
    expect(wrapper.text()).toContain('Entro ahora')
  })

  it('con el flag ya guardado no se muestra, pero se puede volver a ver', async () => {
    const wrapper = await montar()

    expect(wrapper.text()).not.toContain('Fichar al momento vale más')

    await boton(wrapper, '¿Cómo funciona la libreta?').trigger('click')

    expect(wrapper.text()).toContain('Fichar al momento vale más')
  })
})
