import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import type { ResumenMensual } from '../services/resumen'
import ResumenMesView from './ResumenMesView.vue'

vi.mock('../services/resumen', () => ({
  getResumenMes: vi.fn(),
  getInformeMes: vi.fn(),
  getInformeAnio: vi.fn(),
}))

import { getInformeAnio, getInformeMes, getResumenMes } from '../services/resumen'

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
    convenioNombre: 'Convenio Colectivo del Sector de Hostelería y Actividades Turísticas de la Comunidad de Madrid',
    convenioBoletin: 'BOCM',
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
      { path: '/horario', name: 'horario', component: Stub },
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

    // La cifra y el € van en spans separados (la cifra se anima; el € no).
    expect(wrapper.find('.importe .cifra').text()).toBe('38,15')
    expect(wrapper.find('.importe').text()).toContain('€')
    expect(wrapper.find('.importe').attributes('aria-label')).toBe('38,15 euros')
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

  it('422 por horario: guía con enlace al editor de horario, no un error', async () => {
    vi.mocked(getResumenMes).mockRejectedValue(
      new ApiError(422, 'API 422', {
        status: 422,
        detail: 'No has definido tu horario para ese mes',
        codigo: 'HORARIO',
      }),
    )

    const wrapper = await montar()

    expect(wrapper.find('.guia').text()).toContain('No has definido tu horario')
    expect(wrapper.find('.guia a').attributes('href')).toBe('/horario')
    expect(wrapper.find('.error').exists()).toBe(false)
  })

  it('422 por perfil: guía con enlace a la cuenta', async () => {
    vi.mocked(getResumenMes).mockRejectedValue(
      new ApiError(422, 'API 422', {
        status: 422,
        detail: 'Todavía no has creado tu perfil',
        codigo: 'PERFIL',
      }),
    )

    const wrapper = await montar()

    expect(wrapper.find('.guia a').attributes('href')).toBe('/cuenta')
  })

  it('422 DATOS_CONVENIO: sin botón engañoso y explicando que no depende del usuario', async () => {
    vi.mocked(getResumenMes).mockRejectedValue(
      new ApiError(422, 'API 422', {
        status: 422,
        detail: 'Tu convenio no tiene publicada la tabla salarial para tus datos (dimensiones)',
        codigo: 'DATOS_CONVENIO',
      }),
    )

    const wrapper = await montar()

    expect(wrapper.find('.guia').text()).toContain('no tiene publicada la tabla')
    // NADA de "Completar tu perfil": el perfil no tiene la culpa.
    expect(wrapper.find('.guia a').exists()).toBe(false)
    expect(wrapper.find('.guia').text()).toContain('Esto no depende de ti')
  })

  it('422 sin código (backend viejo): se adivina sobre el texto, mejor que sin salida', async () => {
    vi.mocked(getResumenMes).mockRejectedValue(
      new ApiError(422, 'API 422', {
        status: 422,
        detail: 'No has definido tu horario para ese mes',
      }),
    )

    const wrapper = await montar()

    expect(wrapper.find('.guia a').attributes('href')).toBe('/horario')
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

  it('los botones de descarga nombran el mes y el año en pantalla, y siguen al navegar', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())

    const wrapper = await montar()
    const { useResumenStore } = await import('../stores/resumen')
    const { etiquetaMes } = await import('../lib/meses')
    const store = useResumenStore()

    const textoBotones = () =>
      wrapper
        .findAll('button')
        .filter((b) => b.text().includes('Descargar'))
        .map((b) => b.text())

    expect(textoBotones()).toEqual([
      `Descargar el informe de ${etiquetaMes(store.mes)} (PDF)`,
      `Descargar el histórico de ${store.mes.slice(0, 4)} (PDF)`,
    ])

    await wrapper.find('button[aria-label="Mes anterior"]').trigger('click')
    await flushPromises()

    expect(textoBotones()[0]).toBe(`Descargar el informe de ${etiquetaMes(store.mes)} (PDF)`)
  })

  it('descarga el informe del mes en PDF (fetch con token, nunca un <a href> a pelo)', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())
    vi.mocked(getInformeMes).mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }))
    // jsdom no trae createObjectURL; el click de un <a> intentaría "navegar".
    const crearUrl = vi.fn(() => 'blob:falsa')
    const revocarUrl = vi.fn()
    URL.createObjectURL = crearUrl
    URL.revokeObjectURL = revocarUrl
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    const wrapper = await montar()
    const botonInforme = wrapper
      .findAll('button')
      .find((b) => b.text().includes('Descargar el informe'))
    await botonInforme!.trigger('click')
    await flushPromises()

    expect(getInformeMes).toHaveBeenCalledWith(expect.stringMatching(/^\d{4}-\d{2}$/))
    expect(crearUrl).toHaveBeenCalled()
    expect(click).toHaveBeenCalled()
    expect(revocarUrl).toHaveBeenCalledWith('blob:falsa')
    click.mockRestore()
  })

  it('el histórico anual se descarga con el año en pantalla y su propio nombre', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())
    vi.mocked(getInformeAnio).mockResolvedValue(new Blob(['%PDF']))
    URL.createObjectURL = vi.fn(() => 'blob:falsa')
    URL.revokeObjectURL = vi.fn()
    const nombres: string[] = []
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function (this: HTMLAnchorElement) {
        nombres.push(this.download)
      })

    const wrapper = await montar()
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('histórico'))!
      .trigger('click')
    await flushPromises()

    const anio = vi.mocked(getInformeAnio).mock.calls[0][0]
    expect(anio).toMatch(/^\d{4}$/)
    expect(nombres).toEqual([`medeben-historico-${anio}.pdf`])
    click.mockRestore()
  })

  it('doble click en el informe: una sola petición (guard descargando)', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())
    let resuelve!: (b: Blob) => void
    vi.mocked(getInformeMes).mockReturnValue(new Promise((res) => (resuelve = res)))
    URL.createObjectURL = vi.fn(() => 'blob:falsa')
    URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    const wrapper = await montar()
    const botonInforme = wrapper
      .findAll('button')
      .find((b) => b.text().includes('informe'))
    await botonInforme!.trigger('click')
    await botonInforme!.trigger('click')

    expect(getInformeMes).toHaveBeenCalledTimes(1)
    resuelve(new Blob(['%PDF']))
    await flushPromises()
    click.mockRestore()
  })

  it('cambiar de mes con la descarga en vuelo NO desincroniza el nombre del fichero', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())
    let resuelve!: (b: Blob) => void
    vi.mocked(getInformeMes).mockReturnValue(new Promise((res) => (resuelve = res)))
    URL.createObjectURL = vi.fn(() => 'blob:falsa')
    URL.revokeObjectURL = vi.fn()
    const nombres: string[] = []
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function (this: HTMLAnchorElement) {
        nombres.push(this.download)
      })

    const wrapper = await montar()
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('informe'))!
      .trigger('click')
    const mesPedido = vi.mocked(getInformeMes).mock.calls[0][0]
    // Con el PDF en vuelo, el usuario se va al mes anterior.
    await wrapper.find('button[aria-label="Mes anterior"]').trigger('click')
    resuelve(new Blob(['%PDF']))
    await flushPromises()

    // El fichero se llama como el mes PEDIDO, no como el mes en pantalla.
    expect(nombres).toEqual([`medeben-informe-${mesPedido}.pdf`])
    click.mockRestore()
  })

  it('el error del informe se retira al cambiar de mes (no hay avisos fantasma)', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())
    vi.mocked(getInformeMes).mockRejectedValue(
      new ApiError(500, 'API 500', { status: 500, detail: 'No se pudo generar el informe' }),
    )

    const wrapper = await montar()
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('informe'))!
      .trigger('click')
    await flushPromises()
    expect(wrapper.find('.informe [role="alert"]').exists()).toBe(true)

    await wrapper.find('button[aria-label="Mes anterior"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('.informe [role="alert"]').exists()).toBe(false)
  })

  it('si el informe falla, lo dice donde se pidió (aviso local, no un error global)', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())
    vi.mocked(getInformeMes).mockRejectedValue(
      new ApiError(500, 'API 500', { status: 500, detail: 'No se pudo generar el informe' }),
    )

    const wrapper = await montar()
    const botonInforme = wrapper
      .findAll('button')
      .find((b) => b.text().includes('Descargar el informe'))
    await botonInforme!.trigger('click')
    await flushPromises()

    expect(wrapper.find('.informe [role="alert"]').text()).toContain('No se pudo generar el informe')
    // La cifra del mes sigue en pantalla: el fallo del PDF no rompe el resumen.
    expect(wrapper.find('.importe').exists()).toBe(true)
  })

  it('las citas de la cifra están ahí (D18: no me creas, compruébalo)', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(resumenServidor())

    const wrapper = await montar()

    expect(wrapper.text()).toContain('art. 35.1 ET')
    expect(wrapper.text()).toContain('el mínimo de tu convenio')
  })

  it('el disclaimer C5 (punto 1) va bajo la cifra, con el nombre y el año reales del convenio', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(
      resumenServidor({ mes: '2026-07', convenioNombre: 'Convenio de prueba', convenioBoletin: 'BOP de prueba' }),
    )

    const wrapper = await montar()

    expect(wrapper.text()).toContain(
      'Cálculo orientativo según las tablas del convenio Convenio de prueba (2026, BOP de prueba).',
    )
    expect(wrapper.text()).toContain('Verifica con un profesional o tu sindicato antes de reclamar.')
  })

  it('sin boletín del convenio no se inventa nada: solo nombre y año', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(
      resumenServidor({ convenioNombre: 'Convenio de prueba', convenioBoletin: null }),
    )

    const wrapper = await montar()

    expect(wrapper.text()).toContain('según las tablas del convenio Convenio de prueba (2026).')
  })

  it('sin horas extra (0 h) no se enseña el disclaimer del cálculo: no hay cifra que orientar', async () => {
    vi.mocked(getResumenMes).mockResolvedValue(
      resumenServidor({ horasExtra: { minutos: 0, horas: 0 } }),
    )

    const wrapper = await montar()

    expect(wrapper.find('.disclaimer-calculo').exists()).toBe(false)
  })
})
