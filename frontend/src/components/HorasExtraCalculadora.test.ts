import { describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { postHorasExtra } from '../services/convenios'
import { mount } from '@vue/test-utils'
import HorasExtraCalculadora from './HorasExtraCalculadora.vue'

vi.mock('../services/convenios', () => ({
  postHorasExtra: vi.fn(),
}))

function montar(salarioMensualSugerido: number | null) {
  return mount(HorasExtraCalculadora, {
    props: { convenioId: 'madrid-hosteleria', salarioMensualSugerido },
  })
}

function inputSalario(wrapper: ReturnType<typeof montar>) {
  return wrapper.get<HTMLInputElement>('input.input-salario')
}

describe('HorasExtraCalculadora — prellenado del salario', () => {
  it('arranca prellenado con el mínimo sugerido', () => {
    const wrapper = montar(1425.5)

    expect(inputSalario(wrapper).element.value).toBe('1425.5')
  })

  it('si el sugerido cambia después del mount y el usuario NO ha tocado el campo, se actualiza', async () => {
    // Bug de la review: al cambiar de puesto se quedaba el mínimo VIEJO.
    const wrapper = montar(1425.5)

    await wrapper.setProps({ salarioMensualSugerido: 1580.25 })

    expect(inputSalario(wrapper).element.value).toBe('1580.25')
  })

  it('si el sugerido pasa a null sin tocar el campo, se vacía (no queda el mínimo viejo)', async () => {
    const wrapper = montar(1425.5)

    await wrapper.setProps({ salarioMensualSugerido: null })

    expect(inputSalario(wrapper).element.value).toBe('')
  })

  it('si el usuario ha editado el campo, un nuevo sugerido NO lo pisa', async () => {
    const wrapper = montar(1425.5)

    await inputSalario(wrapper).setValue('1600')
    await wrapper.setProps({ salarioMensualSugerido: 1580.25 })

    expect(inputSalario(wrapper).element.value).toBe('1600')
  })
})

describe('HorasExtraCalculadora — desglose del mínimo', () => {
  async function calcularCon(desglose: {
    divisorHoras: number
    esDivisorExplicito: boolean
  }) {
    vi.mocked(postHorasExtra).mockResolvedValue({
      precioHora: 10.8979,
      importe: 54.49,
      desglose: {
        salarioBaseMensual: 1250.91,
        mensualidades: 14,
        plusesAnuales: 2103.42,
        valorHora: 10.8979,
        ...desglose,
      },
      citas: [],
    })
    const wrapper = montar(1250.91)
    await wrapper.findAll('input')[0].setValue('5')
    await wrapper.get('button.boton').trigger('click')
    await flushPromises()
    return wrapper
  }

  it('explica de dónde sale el mínimo con los números del convenio', async () => {
    const wrapper = await calcularCon({ divisorHoras: 1800, esDivisorExplicito: false })

    const desglose = wrapper.get('.desglose')
    expect(desglose.text()).toContain('¿De dónde sale este mínimo?')
    expect(desglose.text()).toContain('1.250,91')
    expect(desglose.text()).toContain('14 pagas')
    expect(desglose.text()).toContain('1.800')
    expect(desglose.text()).toContain('jornada anual')
    expect(desglose.text()).toContain('art. 35')
  })

  it('con divisor explícito del convenio (Tenerife) NO lo vende como jornada anual', async () => {
    // Tenerife no fija jornada anual: divide por las 1.829 h de sus Arts. 23 y 24.
    const wrapper = await calcularCon({ divisorHoras: 1829, esDivisorExplicito: true })

    const desglose = wrapper.get('.desglose')
    expect(desglose.text()).toContain('1.829')
    expect(desglose.text()).toContain('divisor de valor hora que fija tu convenio')
    expect(desglose.text()).not.toContain('jornada anual')
  })
})
