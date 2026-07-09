import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '../services/api'
import type { ConvenioResumen, OcupacionResuelta, SalarioBase } from '../services/convenios'
import PerfilView from './PerfilView.vue'

vi.mock('../services/convenios', () => ({
  getProvincias: vi.fn(),
  getConvenioParaTrabajador: vi.fn(),
  getPuestos: vi.fn(),
  getOcupacion: vi.fn(),
  postSalarioBase: vi.fn(),
}))

import {
  getConvenioParaTrabajador,
  getOcupacion,
  getProvincias,
  getPuestos,
  postSalarioBase,
} from '../services/convenios'

const convenioMadrid: ConvenioResumen = {
  id: 'madrid-hosteleria',
  nombre: 'Convenio de Hostelería de Madrid',
  subsector: 'hosteleria',
  ambitoTipo: 'provincial',
  provincias: ['Madrid'],
  vigenciaDesde: '2024-01-01',
  vigenciaHasta: '2026-12-31',
  fuenteUrl: 'https://www.bocm.es/convenio-hosteleria',
  estado: null,
}

const ocupacionSinPendientes: OcupacionResuelta = {
  dimensiones: { nivel: 'III' },
  pendientes: [],
  articulo: 'Anexo I',
}

const salarioMensual: SalarioBase = {
  importe: 1580.5,
  unidad: 'EUR/mes',
  citas: [{ texto: 'Tabla salarial 2026', url: null }],
}

async function montar() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(PerfilView, { global: { plugins: [pinia] } })
  await flushPromises()
  return wrapper
}

/** Recorre el flujo provincia → subsector hasta tener convenio en pantalla. */
async function llegarAlConvenio(wrapper: Awaited<ReturnType<typeof montar>>) {
  await wrapper.find('#provincia').setValue('Madrid')
  await flushPromises()
  await wrapper.find('.opcion').trigger('click')
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getProvincias).mockResolvedValue(['Madrid', 'Cuenca'])
  vi.mocked(getConvenioParaTrabajador).mockResolvedValue(convenioMadrid)
  vi.mocked(getPuestos).mockResolvedValue([
    { id: 'cocinero', etiqueta: 'Cocinero/a' },
    { id: 'camarero', etiqueta: 'Camarero/a' },
  ])
  vi.mocked(getOcupacion).mockResolvedValue(ocupacionSinPendientes)
  vi.mocked(postSalarioBase).mockResolvedValue(salarioMensual)
})

describe('PerfilView', () => {
  it('carga las provincias al montar y las ofrece en el selector', async () => {
    const wrapper = await montar()

    const opciones = wrapper.find('#provincia').findAll('option')
    expect(opciones.map((o) => o.text())).toContain('Madrid')
    expect(opciones.map((o) => o.text())).toContain('Cuenca')
  })

  it('flujo feliz: provincia y subsector muestran la tarjeta del convenio', async () => {
    const wrapper = await montar()

    await llegarAlConvenio(wrapper)

    expect(getConvenioParaTrabajador).toHaveBeenCalledWith('Madrid', expect.any(String))
    expect(wrapper.text()).toContain('Convenio de Hostelería de Madrid')
    expect(wrapper.find('.tarjeta-enlace').attributes('href')).toBe(
      'https://www.bocm.es/convenio-hosteleria',
    )
  })

  it('elegir puesto sin preguntas pendientes calcula y muestra el salario', async () => {
    const wrapper = await montar()
    await llegarAlConvenio(wrapper)

    await wrapper.find('#puesto').setValue('cocinero')
    await flushPromises()

    // Pedagogía D20: la clasificación se explica con normalidad.
    expect(wrapper.find('.nota-nivel').text()).toContain('nivel III')
    expect(wrapper.find('.resultado .importe').text()).toContain('1.580,50')
    // Salario mensual: sin aviso de unidad rara.
    expect(wrapper.find('.aviso-unidad').exists()).toBe(false)
  })

  it('avisa cuando el convenio publica el salario en una unidad que no es mensual', async () => {
    vi.mocked(postSalarioBase).mockResolvedValue({
      importe: 11.2,
      unidad: 'EUR/hora',
      citas: [],
    })
    const wrapper = await montar()
    await llegarAlConvenio(wrapper)

    await wrapper.find('#puesto').setValue('cocinero')
    await flushPromises()

    expect(wrapper.find('.aviso-unidad').exists()).toBe(true)
  })

  it('con una pregunta pendiente no calcula hasta que el usuario responde', async () => {
    vi.mocked(getOcupacion).mockResolvedValue({
      dimensiones: { nivel: 'III' },
      pendientes: [{ dimension: 'claseEmpresa', valores: ['1ª', '2ª'] }],
      articulo: null,
    })
    const wrapper = await montar()
    await llegarAlConvenio(wrapper)

    await wrapper.find('#puesto').setValue('cocinero')
    await flushPromises()
    expect(postSalarioBase).not.toHaveBeenCalled()

    const botonesPendiente = wrapper.findAll('.paso .opcion').filter((b) => b.text() === '2ª')
    await botonesPendiente[0].trigger('click')
    await flushPromises()

    expect(postSalarioBase).toHaveBeenCalledWith(
      'madrid-hosteleria',
      { nivel: 'III', claseEmpresa: '2ª' },
      expect.any(String),
    )
    expect(wrapper.find('.resultado').exists()).toBe(true)
  })

  it('puesto sin mapear (404) muestra la tarjeta honesta en vez de un error', async () => {
    vi.mocked(getOcupacion).mockRejectedValue(new ApiError(404, 'no mapeado'))
    const wrapper = await montar()
    await llegarAlConvenio(wrapper)

    await wrapper.find('#puesto').setValue('cocinero')
    await flushPromises()

    expect(wrapper.text()).toContain('Todavía no tenemos tu puesto')
    expect(wrapper.find('.error').exists()).toBe(false)
  })

  it('un fallo del API se muestra como alerta accesible', async () => {
    vi.mocked(getConvenioParaTrabajador).mockRejectedValue(new ApiError(500, 'boom'))
    const wrapper = await montar()

    await llegarAlConvenio(wrapper)

    const error = wrapper.find('.error')
    expect(error.exists()).toBe(true)
    expect(error.attributes('role')).toBe('alert')
  })

  it('no enlaza fuentes con URL insegura', async () => {
    vi.mocked(getConvenioParaTrabajador).mockResolvedValue({
      ...convenioMadrid,
      fuenteUrl: 'javascript:alert(1)',
    })
    const wrapper = await montar()

    await llegarAlConvenio(wrapper)

    expect(wrapper.find('.tarjeta-enlace').exists()).toBe(false)
  })
})
