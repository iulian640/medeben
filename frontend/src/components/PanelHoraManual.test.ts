import { describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import PanelHoraManual from './PanelHoraManual.vue'

function boton(wrapper: VueWrapper, texto: string) {
  const encontrado = wrapper.findAll('button').find((b) => b.text() === texto)
  if (!encontrado) {
    throw new Error(`No hay botón "${texto}"`)
  }
  return encontrado
}

describe('PanelHoraManual', () => {
  it('cerrado, el formulario queda inerte; el toggle pide abrir vía update:abierto', async () => {
    const wrapper = mount(PanelHoraManual, {
      props: { abierto: false, hora: '', fichando: false },
    })

    // El form vive siempre en el DOM (PanelPlegable lo despliega animado);
    // cerrado, lo que lo saca de la interacción es el inert, no su ausencia.
    expect(wrapper.find('.plegable').attributes()).toHaveProperty('inert')
    expect(boton(wrapper, 'Registrar el turno manualmente').attributes('aria-expanded')).toBe('false')

    await boton(wrapper, 'Registrar el turno manualmente').trigger('click')

    expect(wrapper.emitted('update:abierto')).toStrictEqual([[true]])
  })

  it('abierto, el formulario es alcanzable (sin inert)', () => {
    const wrapper = mount(PanelHoraManual, {
      props: { abierto: true, hora: '', fichando: false },
    })

    expect(wrapper.find('.plegable').attributes('inert')).toBeUndefined()
    expect(wrapper.find('form').exists()).toBe(true)
  })

  it('el submit del form (Intro) ficha la ENTRADA, la acción más común', async () => {
    const wrapper = mount(PanelHoraManual, {
      props: { abierto: true, hora: '09:00', fichando: false },
    })

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('fichar')).toStrictEqual([['ENTRADA']])
  })

  it('el botón "Salida a esa hora" ficha una SALIDA', async () => {
    const wrapper = mount(PanelHoraManual, {
      props: { abierto: true, hora: '09:00', fichando: false },
    })

    await boton(wrapper, 'Salida a esa hora').trigger('click')

    expect(wrapper.emitted('fichar')).toStrictEqual([['SALIDA']])
  })

  it('sin hora los dos botones quedan deshabilitados', () => {
    const wrapper = mount(PanelHoraManual, {
      props: { abierto: true, hora: '', fichando: false },
    })

    expect(boton(wrapper, 'Entrada a esa hora').attributes('disabled')).toBeDefined()
    expect(boton(wrapper, 'Salida a esa hora').attributes('disabled')).toBeDefined()
  })

  it('con un apunte en vuelo (fichando) los botones quedan deshabilitados', () => {
    const wrapper = mount(PanelHoraManual, {
      props: { abierto: true, hora: '09:00', fichando: true },
    })

    expect(boton(wrapper, 'Entrada a esa hora').attributes('disabled')).toBeDefined()
    expect(boton(wrapper, 'Salida a esa hora').attributes('disabled')).toBeDefined()
  })
})
