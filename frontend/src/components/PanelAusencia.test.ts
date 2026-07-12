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
  it('cerrado, el formulario queda inerte (el toggle vive en el padre)', () => {
    const wrapper = mount(PanelAusencia, {
      props: { abierto: false, motivo: '', fichando: false },
    })

    // El form vive siempre en el DOM (PanelPlegable lo despliega animado);
    // cerrado, lo que lo saca de la interacción es el inert, no su ausencia.
    // El botón que lo abre es del padre: la fila de excepciones de la libreta.
    expect(wrapper.find('.plegable').attributes()).toHaveProperty('inert')
  })

  it('abierto avisa de la privacidad del motivo (disclaimer C5, punto 3: sin la palabra "cifrado" hasta que exista C4)', () => {
    const wrapper = mount(PanelAusencia, {
      props: { abierto: true, motivo: '', fichando: false },
    })

    expect(wrapper.text()).toContain(
      'El motivo es opcional. Solo se usa para tu propia reclamación y nunca se comparte. Si prefieres, déjalo en blanco.',
    )
    expect(wrapper.text()).not.toContain('cifrado')
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
