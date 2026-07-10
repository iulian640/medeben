import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PanelRectificacionSellado from './PanelRectificacionSellado.vue'

describe('PanelRectificacionSellado', () => {
  it('explica en cristiano el sellado y la rectificación tardía', () => {
    const wrapper = mount(PanelRectificacionSellado, { props: { fichando: false } })

    expect(wrapper.text()).toContain('Este día ya quedó protegido')
    expect(wrapper.text()).toContain('rectificación tardía')
    expect(wrapper.text()).toContain('lo ya protegido no se toca')
  })

  it('solo deja confirmar tras marcar la casilla, con su fricción', async () => {
    const wrapper = mount(PanelRectificacionSellado, { props: { fichando: false } })

    const boton = wrapper.get('button')
    expect(boton.attributes('disabled')).toBeDefined()

    await wrapper.find('input[type="checkbox"]').setValue(true)
    expect(boton.attributes('disabled')).toBeUndefined()

    await boton.trigger('click')
    expect(wrapper.emitted('confirmar')).toHaveLength(1)
    // El evento lleva el estado del checkbox: el padre lo re-comprueba antes
    // de mandar la petición (comprobación redundante a propósito, review).
    expect(wrapper.emitted('confirmar')![0]).toEqual([true])
  })

  it('con un apunte en vuelo (fichando) el botón sigue deshabilitado aunque esté marcada', async () => {
    const wrapper = mount(PanelRectificacionSellado, { props: { fichando: true } })

    await wrapper.find('input[type="checkbox"]').setValue(true)

    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
  })
})
