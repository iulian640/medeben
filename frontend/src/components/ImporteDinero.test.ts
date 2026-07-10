import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ImporteDinero from './ImporteDinero.vue'

// En jsdom no hay matchMedia, así que lib/animacion salta al estado final
// de forma síncrona: aquí se prueba el contrato visible, no los frames.
describe('ImporteDinero', () => {
  it('enseña el importe en estilo español con el euro al lado', () => {
    const wrapper = mount(ImporteDinero, { props: { importe: 1425.5 } })
    expect(wrapper.find('.cifra').text()).toBe('1.425,50')
    expect(wrapper.find('.euro').text()).toBe('€')
  })

  it('lleva el valor final en aria-label y oculta la cuenta al lector', () => {
    const wrapper = mount(ImporteDinero, { props: { importe: 127.4 } })
    expect(wrapper.find('p').attributes('aria-label')).toBe('127,40 euros')
    expect(wrapper.find('.linea').attributes('aria-hidden')).toBe('true')
  })

  it('actualiza la cifra cuando cambia el importe (cambio de mes)', async () => {
    const wrapper = mount(ImporteDinero, { props: { importe: 10 } })
    await wrapper.setProps({ importe: 99.99 })
    await nextTick()
    expect(wrapper.find('.cifra').text()).toBe('99,99')
    expect(wrapper.find('p').attributes('aria-label')).toBe('99,99 euros')
  })

  it('el trazo del subrayado queda visible (sin transform residual)', () => {
    const wrapper = mount(ImporteDinero, { props: { importe: 50 } })
    const trazo = wrapper.find('.trazo').element as HTMLElement
    expect(trazo.style.transform).toBe('')
  })
})
