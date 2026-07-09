import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import type { ResumenMensual } from '../services/resumen'
import ResumenMesView from './ResumenMesView.vue'

vi.mock('../services/resumen', () => ({
  getResumenMes: vi.fn(),
}))

import { getResumenMes } from '../services/resumen'

const Stub = { template: '<div />' }

function resumenServidor(extra: Partial<ResumenMensual> = {}): ResumenMensual {
  return {
    mes: '2026-07',
    minutosTeoricos: 9600,
    minutosReales: 9780,
    horasExtra: { minutos: 210, horas: 3.5 },
    deficitInformativo: { minutos: 60, horas: 1 },
    diasSinCalcular: 1,
    contadoresPorEstado: { COMPLETO: 20 },
    importeEstimado: {
      horasExtra: 3.5,
      precioHora: 10.9,
      importe: 38.15,
      salarioBaseAplicado: 1250.91,
      salarioRealUsado: false,
      desglose: {
        salarioBaseMensual: 1250.91,
        mensualidades: 14,
        plusesAnuales: 0,
        divisorHoras: 1800,
        esDivisorExplicito: false,
        valorHora: 10.9,
      },
      citas: [{ texto: 'La hora extra no puede pagarse por debajo de la ordinaria (art. 35.1 ET)', url: 'https://www.boe.es/eli/es/rdlg/2015/10/23/2' }],
    },
    topeAnual: { horas: 80, acumuladoAnioHoras: 3.5, citas: [] },
    avisos: [],
    ...extra,
  }
}

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/resumen', name: 'resumen', component: ResumenMesView },
      { path: '/libreta', name: 'libreta', component: Stub },
      { path: '/cuenta', name: 'cuenta', component: Stub },
    ],
  })
}

async function montar() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = crearRouter()
  await router.push('/resumen')
  const wrapper = mount(ResumenMesView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ResumenMesView', () => {
  it('mientras carga: "Echando cuentas..." con role=status y sin cifra a medias', async () => {
    let resuelve!: (r: ResumenMensual) => void
    vi.mocked(getResumenMes).mockReturnValue(new Promise((res) => (resuelve = res)))

    const wrapper = await montar()

    expect(wrapper.find('[role="status"]').text()).toContain('Echando cuentas')
    expect(wrapper.find('.importe').exists()).toBe(false)
    // Los botones de mes no disparan peticiones extra durante la carga.
    expect(wrapper.find('button[aria-label="Mes anterior"]').attributes('disabled')).toBeDefined()

    resuelve(resumenServidor())
    await flushPromises()
    expect(wrapper.find('.importe').exists()).toBe(true)
  })

  it('las horas fraccionarias del tope van en estilo español (3,5, no 3.5)', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(
      resumenServidor({ topeAnual: { horas: 80, acumuladoAnioHoras: 3.5, citas: [] } }),
    )

    const wrapper = await montar()

    expect(wrapper.text()).toContain('3,5 h extra')
    expect(wrapper.text()).not.toContain('3.5 h extra')
  })

  it('el número gordo: importe, horas extra y precio hora en cristiano', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())

    const wrapper = await montar()

    expect(wrapper.find('.importe').text()).toContain('38,15 €')
    expect(wrapper.text()).toContain('te deben, como mínimo')
    expect(wrapper.text()).toContain('3 h 30 min extra')
    expect(wrapper.text()).toContain('10,90 € la hora')
  })

  it('sin horas extra no hay drama: 0 h y una explicación amable', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(
      resumenServidor({ horasExtra: { minutos: 0, horas: 0 } }),
    )

    const wrapper = await montar()

    expect(wrapper.find('.importe').text()).toBe('0 h')
    expect(wrapper.text()).toContain('aquí verás lo que te deben')
    expect(wrapper.text()).not.toContain('te deben, como mínimo')
  })

  it('el mes en horas: teórico, real, déficit informativo y días sin calcular', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())

    const wrapper = await montar()

    expect(wrapper.text()).toContain('Según tu horario')
    expect(wrapper.text()).toContain('160 h') // 9600 min
    expect(wrapper.text()).toContain('163 h') // 9780 min
    expect(wrapper.text()).toContain('Horas de menos (informativo)')
    expect(wrapper.text()).toContain('1 día quedó sin calcular')
  })

  it('el tope anual se pinta como barra de progreso con las horas del año', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(
      resumenServidor({ topeAnual: { horas: 80, acumuladoAnioHoras: 72, citas: [] } }),
    )

    const wrapper = await montar()

    const barra = wrapper.find('[role="progressbar"]')
    expect(barra.attributes('aria-valuenow')).toBe('90')
    expect(wrapper.find('.barra-tope-relleno').classes()).toContain('alerta')
    expect(wrapper.text()).toContain('72 h extra')
  })

  it('los avisos del backend (tope D22) se muestran como alerta', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(
      resumenServidor({ avisos: ['Estás cerca del tope anual de 80 h extraordinarias'] }),
    )

    const wrapper = await montar()

    expect(wrapper.find('.aviso').text()).toContain('cerca del tope anual')
  })

  it('422 por horario: guía con enlace a la libreta, no un error', async () => {
    vi.mocked(getResumenMes).mockRejectedValue(
      new ApiError(422, 'API 422', {
        status: 422,
        detail: 'No has definido tu horario para ese mes',
      }),
    )

    const wrapper = await montar()

    expect(wrapper.find('.guia').text()).toContain('No has definido tu horario')
    expect(wrapper.find('.guia a').attributes('href')).toBe('/libreta')
    expect(wrapper.find('.error').exists()).toBe(false)
  })

  it('422 por perfil: guía con enlace a la cuenta', async () => {
    vi.mocked(getResumenMes).mockRejectedValue(
      new ApiError(422, 'API 422', {
        status: 422,
        detail: 'Todavía no has creado tu perfil',
      }),
    )

    const wrapper = await montar()

    expect(wrapper.find('.guia a').attributes('href')).toBe('/cuenta')
  })

  it('un 500 sale como error con reintento', async () => {
    vi.mocked(getResumenMes).mockRejectedValue(
      new ApiError(500, 'API 500', { status: 500, detail: 'Error interno' }),
    )

    const wrapper = await montar()

    expect(wrapper.find('.error').text()).toContain('Error interno')
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())
    await wrapper.find('.error button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.importe').exists()).toBe(true)
  })

  it('navegación de meses: el → está deshabilitado en el mes actual', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())

    const wrapper = await montar()

    const siguiente = wrapper.find('button[aria-label="Mes siguiente"]')
    expect(siguiente.attributes('disabled')).toBeDefined()

    await wrapper.find('button[aria-label="Mes anterior"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('button[aria-label="Mes siguiente"]').attributes('disabled')).toBeUndefined()
  })

  it('las citas de la cifra están ahí (D18: no me creas, compruébalo)', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())

    const wrapper = await montar()

    expect(wrapper.text()).toContain('art. 35.1 ET')
    expect(wrapper.text()).toContain('el mínimo de tu convenio')
  })
})
