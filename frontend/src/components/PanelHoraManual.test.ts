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
  it('cerrado solo enseña el toggle; al pulsarlo pide abrir vía update:abierto', async () => {
    const wrapper = mount(PanelHoraManual, {
      props: { abierto: false, hora: '', fichando: false },
    })

    expect(wrapper.find('form').exists()).toBe(false)
    expect(boton(wrapper, '¿A otra hora?').attributes('aria-expanded')).toBe('false')

    await boton(wrapper, '¿A otra hora?').trigger('click')

    expect(wrapper.emitted('update:abierto')).toStrictEqual([[true]])
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
