import { describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import PanelAusencia from './PanelAusencia.vue'

function boton(wrapper: VueWrapper, texto: string) {
  const encontrado = wrapper.findAll('button').find((b) => b.text() === texto)
  if (!encontrado) {
    throw new Error(`No hay botón "${texto}"`)
  }
  return encontrado
}

describe('PanelAusencia', () => {
  it('cerrado, el formulario queda inerte; al pulsar el toggle pide abrir vía update:abierto', async () => {
    const wrapper = mount(PanelAusencia, {
      props: { abierto: false, motivo: '', fichando: false },
    })

    // El form vive siempre en el DOM (PanelPlegable lo despliega animado);
    // cerrado, lo que lo saca de la interacción es el inert, no su ausencia.
    expect(wrapper.find('.plegable').attributes()).toHaveProperty('inert')

    await boton(wrapper, 'No he ido').trigger('click')

    expect(wrapper.emitted('update:abierto')).toStrictEqual([[true]])
  })

  it('abierto avisa de la privacidad del motivo', () => {
    const wrapper = mount(PanelAusencia, {
      props: { abierto: true, motivo: '', fichando: false },
    })

    expect(wrapper.text()).toContain('El motivo es opcional; si lo escribes, queda en tu libreta.')
  })

  it('el submit del form (Intro o botón) pide registrar la ausencia', async () => {
    const wrapper = mount(PanelAusencia, {
      props: { abierto: true, motivo: '', fichando: false },
    })

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('registrar')).toHaveLength(1)
  })

  it('con un apunte en vuelo (fichando) el botón queda deshabilitado', () => {
    const wrapper = mount(PanelAusencia, {
      props: { abierto: true, motivo: '', fichando: true },
    })

    expect(boton(wrapper, 'Registrar ausencia').attributes('disabled')).toBeDefined()
  })
})
