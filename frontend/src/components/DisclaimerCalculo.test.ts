import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DisclaimerCalculo from './DisclaimerCalculo.vue'

describe('DisclaimerCalculo', () => {
  it('con boletín: cita el convenio, el año y el boletín reales (disclaimer C5, punto 1)', () => {
    const wrapper = mount(DisclaimerCalculo, {
      props: {
        nombre: 'Convenio Colectivo del Sector de Hostelería y Actividades Turísticas de la Comunidad de Madrid',
        anio: '2026',
        boletin: 'BOCM',
      },
    })

    expect(wrapper.text()).toContain(
      'Cálculo orientativo según las tablas del convenio Convenio Colectivo del Sector de '
        + 'Hostelería y Actividades Turísticas de la Comunidad de Madrid (2026, BOCM). Puede '
        + 'contener errores o no reflejar tu situación concreta. Verifica con un profesional '
        + 'o tu sindicato antes de reclamar.',
    )
  })

  it('sin boletín: no lo inventa, muestra solo nombre y año', () => {
    const wrapper = mount(DisclaimerCalculo, {
      props: {
        nombre: 'Convenio colectivo del sector Hostelería y Similares del Principado de Asturias',
        anio: '2026',
        boletin: null,
      },
    })

    expect(wrapper.text()).toContain(
      'según las tablas del convenio Convenio colectivo del sector Hostelería y Similares '
        + 'del Principado de Asturias (2026).',
    )
    expect(wrapper.text()).not.toContain('(2026, null)')
    expect(wrapper.text()).not.toContain('(2026,)')
  })

  it('nunca usa v-html: el nombre del convenio se interpola como texto', () => {
    const wrapper = mount(DisclaimerCalculo, {
      props: { nombre: '<img src=x onerror=alert(1)>', anio: '2026', boletin: null },
    })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
  })
})
