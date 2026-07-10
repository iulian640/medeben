import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import LibretaOnboarding from './LibretaOnboarding.vue'

describe('LibretaOnboarding', () => {
  it('recorre los 3 pasos del copy de D38 y al final emite cerrar', async () => {
    const wrapper = mount(LibretaOnboarding)

    // Paso 1: fichar al momento vale más como prueba.
    expect(wrapper.text()).toContain('Fichar al momento vale más')
    expect(wrapper.text()).toContain('1 de 3')
    await wrapper.find('button').trigger('click')

    // Paso 2: a los 14 días cada día queda protegido.
    expect(wrapper.text()).toContain('A los 14 días, cada día queda protegido')
    await wrapper.find('button').trigger('click')

    // Paso 3: los huecos son normales y dan credibilidad.
    expect(wrapper.text()).toContain('Los huecos son normales')
    expect(wrapper.text()).toContain('credibilidad')
    expect(wrapper.find('button').text()).toBe('Empezar a fichar')
    expect(wrapper.emitted('cerrar')).toBeUndefined()

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('cerrar')).toHaveLength(1)
  })
})
