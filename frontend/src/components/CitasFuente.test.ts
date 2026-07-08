import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CitasFuente from './CitasFuente.vue'

describe('CitasFuente', () => {
  it('renderiza el texto de la cita y el enlace al boletín oficial', () => {
    const wrapper = mount(CitasFuente, {
      props: {
        citas: [{ texto: 'Salario base mínimo de 1425.50 EUR/mes (Art. 12)', url: 'https://www.boe.es/x' }],
      },
    })

    expect(wrapper.text()).toContain('Salario base mínimo de 1425.50 EUR/mes (Art. 12)')
    const enlace = wrapper.get('a')
    expect(enlace.attributes('href')).toBe('https://www.boe.es/x')
    expect(enlace.attributes('target')).toBe('_blank')
    expect(enlace.attributes('rel')).toContain('noopener')
  })

  it('escapa el HTML del texto: nunca lo interpreta (sin v-html)', () => {
    const wrapper = mount(CitasFuente, {
      props: {
        citas: [{ texto: '<img src=x onerror=alert(1)> cita maliciosa', url: 'https://boe.es' }],
      },
    })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)> cita maliciosa')
  })

  it('no pinta enlace si la url no es http(s): javascript: queda bloqueado', () => {
    const wrapper = mount(CitasFuente, {
      props: { citas: [{ texto: 'Cita con url maliciosa', url: 'javascript:alert(1)' }] },
    })

    expect(wrapper.find('a').exists()).toBe(false)
    expect(wrapper.text()).toContain('Cita con url maliciosa')
  })

  it('no pinta enlace si la cita viene sin url', () => {
    const wrapper = mount(CitasFuente, {
      props: { citas: [{ texto: 'Aplicada por ultraactividad', url: null }] },
    })

    expect(wrapper.find('a').exists()).toBe(false)
    expect(wrapper.text()).toContain('Aplicada por ultraactividad')
  })
})
