import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PanelPlegable from './PanelPlegable.vue'

describe('PanelPlegable', () => {
  it('cerrado, el contenido queda inerte (ni foco ni lectores)', () => {
    const wrapper = mount(PanelPlegable, {
      props: { abierto: false },
      slots: { default: '<button>Dentro</button>' },
    })
    expect(wrapper.find('.plegable').attributes()).toHaveProperty('inert')
    expect(wrapper.find('.plegable').classes()).not.toContain('abierto')
  })

  it('abierto, el contenido es alcanzable y se marca la clase de despliegue', () => {
    const wrapper = mount(PanelPlegable, {
      props: { abierto: true },
      slots: { default: '<button>Dentro</button>' },
    })
    expect(wrapper.find('.plegable').attributes('inert')).toBeUndefined()
    expect(wrapper.find('.plegable').classes()).toContain('abierto')
    expect(wrapper.find('button').text()).toBe('Dentro')
  })

  it('reacciona al cambio de abierto sin desmontar el contenido', async () => {
    const wrapper = mount(PanelPlegable, {
      props: { abierto: false },
      slots: { default: '<p>Formulario</p>' },
    })
    await wrapper.setProps({ abierto: true })
    expect(wrapper.find('.plegable').classes()).toContain('abierto')
    expect(wrapper.find('p').text()).toBe('Formulario')
  })
})
