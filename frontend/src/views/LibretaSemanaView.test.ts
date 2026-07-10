import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import type { EstadoDia, EstadoDiaGuardado } from '../services/fichajes'
import type { HorarioEfectivo } from '../services/horario'
import LibretaSemanaView from './LibretaSemanaView.vue'

vi.mock('../services/fichajes', () => ({
  getEstadoDia: vi.fn(),
  postApunte: vi.fn(),
}))
vi.mock('../services/horario', () => ({
  getHorarioSemana: vi.fn(),
}))

import { getEstadoDia } from '../services/fichajes'
import { getHorarioSemana } from '../services/horario'

/** Semana del lunes 2026-07-06 (el "hoy" de los tests es el miércoles 8). */
function dia(fecha: string, estado: EstadoDia, minutos: number | null): EstadoDiaGuardado {
  return {
    fecha,
    estado,
    sellado: false,
    selladoDesde: '2026-07-21',
    minutosTrabajados: minutos,
    tramos: [],
    entradaAbierta: null,
    apuntes: [],
  }
}

const semanaServidor: Record<string, EstadoDiaGuardado> = {
  '2026-07-06': dia('2026-07-06', 'COMPLETO', 450),
  '2026-07-07': dia('2026-07-07', 'AUSENCIA', null),
  '2026-07-08': dia('2026-07-08', 'EN_CURSO', null),
  '2026-07-09': dia('2026-07-09', 'PENDIENTE', null),
  '2026-07-10': dia('2026-07-10', 'PENDIENTE', null),
  '2026-07-11': dia('2026-07-11', 'HUECO', null),
  '2026-07-12': dia('2026-07-12', 'PENDIENTE', null),
}

const horarioServidor: HorarioEfectivo = {
  dias: [
    { tramos: [{ entrada: '09:00', salida: '17:00' }] },
    { tramos: [{ entrada: '09:00', salida: '17:00' }] },
    { tramos: [{ entrada: '09:00', salida: '17:00' }] },
    { tramos: [{ entrada: '09:00', salida: '17:00' }] },
    { tramos: [{ entrada: '09:00', salida: '17:00' }] },
    { tramos: [] },
    { tramos: [] },
  ],
  origen: 'SEMANA_TIPO',
  definidoEn: '2026-07-01T10:00:00+02:00',
}

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/libreta', name: 'libreta', component: Stub },
      { path: '/libreta/semana', name: 'libreta-semana', component: LibretaSemanaView },
      { path: '/libreta/dia/:fecha', name: 'libreta-dia', component: Stub },
    ],
  })
}

async function montar(): Promise<VueWrapper> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = crearRouter()
  await router.push('/libreta/semana')
  const wrapper = mount(LibretaSemanaView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

function boton(wrapper: VueWrapper, texto: string) {
  const encontrado = wrapper.findAll('button').find((b) => b.text() === texto)
  if (!encontrado) {
    throw new Error(`No hay botón "${texto}"`)
  }
  return encontrado
}

beforeEach(() => {
  vi.clearAllMocks()
  // "Hoy" es el miércoles 2026-07-08: la semana en pantalla arranca el lunes 6.
  vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 6, 8, 12, 0) })
  vi.mocked(getEstadoDia).mockImplementation((fecha) =>
    Promise.resolve(semanaServidor[fecha] ?? dia(fecha, 'PENDIENTE', null)),
  )
  vi.mocked(getHorarioSemana).mockResolvedValue(horarioServidor)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('LibretaSemanaView', () => {
  it('pinta los 7 días con su estado en cristiano', async () => {
    const wrapper = await montar()

    const dias = wrapper.findAll('.dia')
    expect(dias).toHaveLength(7)
    expect(wrapper.text()).toContain('Semana del 06/07/2026')
    expect(dias[0].text()).toContain('lunes')
    expect(dias[0].text()).toContain('Completo')
    expect(dias[1].text()).toContain('No fuiste, y quedó apuntado')
    expect(dias[2].text()).toContain('En curso: falta la salida')
    expect(dias[3].text()).toContain('Sin apuntar todavía')
    expect(dias[6].text()).toContain('domingo')
  })

  it('cada día pasado (u hoy) es un enlace que abre esa fecha; los futuros no', async () => {
    const wrapper = await montar()

    // "Hoy" es el miércoles 8: lunes, martes y miércoles se abren.
    const enlaces = wrapper.findAll('a.dia-enlace')
    expect(enlaces.map((e) => e.attributes('href'))).toEqual([
      '/libreta/dia/2026-07-06',
      '/libreta/dia/2026-07-07',
      '/libreta/dia/2026-07-08',
    ])
    // El jueves aún no ha llegado: fila sin enlace, no hay nada que fichar en él.
    expect(wrapper.findAll('.dia')[3].find('a').exists()).toBe(false)
  })

  it('los minutos salen formateados, y los null como "—" con aviso de sin calcular', async () => {
    const wrapper = await montar()

    const dias = wrapper.findAll('.dia')
    expect(dias[0].text()).toContain('7 h 30 min')
    const sinCalcular = dias[2].find('.sin-calcular')
    expect(sinCalcular.text()).toBe('—')
    expect(sinCalcular.attributes('title')).toBe('sin calcular')
  })

  it('el hueco se enseña sin dramatismo, con la nota de que es normal', async () => {
    const wrapper = await montar()

    expect(wrapper.findAll('.dia')[5].text()).toContain('Hueco: quedó sin apuntar')
    expect(wrapper.text()).toContain('un diario real tiene huecos')
  })

  it('compara con las horas teóricas del horario cuando existe', async () => {
    const wrapper = await montar()

    const dias = wrapper.findAll('.dia')
    expect(dias[0].text()).toContain('según tu horario: 8 h')
    expect(dias[5].text()).toContain('libre según tu horario')
  })

  it('sin horario configurado (404) no hay comparación ni error', async () => {
    vi.mocked(getHorarioSemana).mockRejectedValue(
      new ApiError(404, 'API 404', { status: 404, detail: 'No hay horario para esa semana' }),
    )

    const wrapper = await montar()

    expect(wrapper.findAll('.dia')).toHaveLength(7)
    expect(wrapper.text()).not.toContain('según tu horario')
    expect(wrapper.text()).toContain('Sin horario configurado')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('navega a la semana anterior y a la siguiente', async () => {
    const wrapper = await montar()

    await boton(wrapper, '← Anterior').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Semana del 29/06/2026')
    expect(getEstadoDia).toHaveBeenCalledWith('2026-06-29')

    await boton(wrapper, 'Siguiente →').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Semana del 06/07/2026')
  })

  it('un día caído no tumba la semana: se marca aparte y se puede reintentar', async () => {
    vi.mocked(getEstadoDia).mockImplementation((fecha) => {
      if (fecha === '2026-07-09') {
        return Promise.reject(new ApiError(500, 'API 500', { status: 500, detail: 'Error interno' }))
      }
      return Promise.resolve(semanaServidor[fecha] ?? dia(fecha, 'PENDIENTE', null))
    })

    const wrapper = await montar()

    const dias = wrapper.findAll('.dia')
    expect(dias).toHaveLength(7)
    expect(dias[3].text()).toContain('No se ha podido cargar')
    expect(wrapper.text()).toContain(
      'Algún día no se ha podido cargar; el resto de la semana sí. Puedes reintentar.',
    )

    vi.mocked(getEstadoDia).mockImplementation((fecha) =>
      Promise.resolve(semanaServidor[fecha] ?? dia(fecha, 'PENDIENTE', null)),
    )
    await boton(wrapper, 'Reintentar').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.dia')[3].text()).toContain('Sin apuntar todavía')
  })

  it('si la carga falla, enseña el error legible y deja reintentar', async () => {
    vi.mocked(getEstadoDia).mockRejectedValue(
      new ApiError(500, 'API 500', { status: 500, detail: 'Error interno' }),
    )

    const wrapper = await montar()

    expect(wrapper.find('[role="alert"]').text()).toContain('Error interno')

    vi.mocked(getEstadoDia).mockImplementation((fecha) =>
      Promise.resolve(semanaServidor[fecha] ?? dia(fecha, 'PENDIENTE', null)),
    )
    await boton(wrapper, 'Reintentar').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.dia')).toHaveLength(7)
  })
})
