import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import type { Cuadrante, HorarioEfectivo } from '../services/horario'
import HorarioView from './HorarioView.vue'

vi.mock('../services/horario', () => ({
  getSemanaTipo: vi.fn(),
  getHorarioSemana: vi.fn(),
  putSemanaTipo: vi.fn(),
  putSemana: vi.fn(),
}))

import { getHorarioSemana, getSemanaTipo, putSemana, putSemanaTipo } from '../services/horario'

const Stub = { template: '<div />' }

/** L-V de 10:00 a 18:00, finde libre. */
const semanaTipoServidor: Cuadrante = {
  semanaInicio: null,
  dias: [
    ...Array.from({ length: 5 }, () => ({ tramos: [{ entrada: '10:00', salida: '18:00' }] })),
    { tramos: [] },
    { tramos: [] },
  ],
  creadoEn: '2026-07-01T10:00:00+02:00',
}

const efectivoServidor: HorarioEfectivo = {
  dias: semanaTipoServidor.dias,
  origen: 'SEMANA_TIPO',
  definidoEn: '2026-07-01T10:00:00+02:00',
}

const noHay = (detail: string) => new ApiError(404, 'API 404', { status: 404, detail })

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/horario', name: 'horario', component: HorarioView },
      { path: '/horario/semana/:lunes', name: 'horario-semana', component: HorarioView },
      { path: '/libreta/semana', name: 'libreta-semana', component: Stub },
    ],
  })
}

async function montarConRouter(ruta = '/horario'): Promise<{ wrapper: VueWrapper; router: Router }> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = crearRouter()
  await router.push(ruta)
  const wrapper = mount(HorarioView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router }
}

async function montar(ruta = '/horario'): Promise<VueWrapper> {
  return (await montarConRouter(ruta)).wrapper
}

function boton(wrapper: VueWrapper, texto: string) {
  const encontrado = wrapper.findAll('button').find((b) => b.text().includes(texto))
  if (!encontrado) {
    throw new Error(`No hay botón "${texto}"`)
  }
  return encontrado
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getSemanaTipo).mockResolvedValue(semanaTipoServidor)
  vi.mocked(getHorarioSemana).mockResolvedValue(efectivoServidor)
  vi.mocked(putSemanaTipo).mockResolvedValue(semanaTipoServidor)
  vi.mocked(putSemana).mockResolvedValue({ ...semanaTipoServidor, semanaInicio: '2026-07-06' })
})

describe('HorarioView — semana tipo (/horario)', () => {
  it('carga la semana tipo: 7 días con sus horas y el total semanal', async () => {
    const wrapper = await montar()

    expect(wrapper.find('h1').text()).toBe('Tu horario')
    const dias = wrapper.findAll('.dia')
    expect(dias).toHaveLength(7)
    expect(dias[0].text()).toContain('Lunes')
    expect(dias[0].text()).toContain('8 h')
    expect(dias[5].text()).toContain('libre')
    expect(wrapper.text()).toContain('Total a la semana')
    expect(wrapper.text()).toContain('40 h')
  })

  it('sin semana tipo todavía (404): editor en blanco, no un error', async () => {
    vi.mocked(getSemanaTipo).mockRejectedValue(noHay('Todavía no has creado tu horario'))

    const wrapper = await montar()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.findAll('.dia')).toHaveLength(7)
    expect(wrapper.findAll('input[type="time"]')).toHaveLength(0)
    expect(wrapper.findAll('.dia')[0].text()).toContain('libre')
  })

  it('guarda con putSemanaTipo los 7 días y confirma en verde', async () => {
    const wrapper = await montar()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(putSemanaTipo).toHaveBeenCalledTimes(1)
    const dias = vi.mocked(putSemanaTipo).mock.calls[0][0]
    expect(dias).toHaveLength(7)
    expect(dias[0].tramos).toEqual([{ entrada: '10:00', salida: '18:00' }])
    expect(dias[6].tramos).toEqual([])
    expect(putSemana).not.toHaveBeenCalled()
    expect(wrapper.find('[role="status"]').text()).toContain('Horario guardado')
    expect(wrapper.text()).toContain('desde esta semana en adelante')
  })

  it('añadir turno mete un tramo vacío; el 2º es el partido y no hay 3º', async () => {
    vi.mocked(getSemanaTipo).mockRejectedValue(noHay('Todavía no has creado tu horario'))
    const wrapper = await montar()

    const lunes = wrapper.findAll('.dia')[0]
    await lunes.findAll('button').find((b) => b.text() === 'Añadir turno')!.trigger('click')
    expect(lunes.findAll('input[type="time"]')).toHaveLength(2)

    await lunes
      .findAll('button')
      .find((b) => b.text().includes('turno partido'))!
      .trigger('click')
    expect(lunes.findAll('input[type="time"]')).toHaveLength(4)
    // Con 2 tramos ya no se ofrece añadir más.
    expect(lunes.findAll('button').some((b) => b.text().startsWith('Añadir'))).toBe(false)
  })

  it('con un tramo a medio rellenar no deja guardar (mejor ausente que erróneo)', async () => {
    const wrapper = await montar()

    const sabado = wrapper.findAll('.dia')[5]
    await sabado.findAll('button').find((b) => b.text() === 'Añadir turno')!.trigger('click')

    expect(boton(wrapper, 'Guardar el horario').attributes('disabled')).toBeDefined()
    await wrapper.find('form').trigger('submit')
    expect(putSemanaTipo).not.toHaveBeenCalled()
  })

  it('"Como el <día anterior>" copia los tramos sin compartir la referencia', async () => {
    const wrapper = await montar()

    const sabado = wrapper.findAll('.dia')[5]
    await sabado.findAll('button').find((b) => b.text() === 'Como el viernes')!.trigger('click')
    expect(sabado.findAll('input[type="time"]')).toHaveLength(2)
    expect((sabado.find('input[type="time"]').element as HTMLInputElement).value).toBe('10:00')

    // Editar el sábado no toca el viernes: la copia es profunda.
    await sabado.find('input[type="time"]').setValue('12:00')
    const viernes = wrapper.findAll('.dia')[4]
    expect((viernes.find('input[type="time"]').element as HTMLInputElement).value).toBe('10:00')
  })

  it('quitar el único tramo deja el día libre', async () => {
    const wrapper = await montar()

    const lunes = wrapper.findAll('.dia')[0]
    await lunes.findAll('button').find((b) => b.text() === 'Quitar')!.trigger('click')

    expect(lunes.findAll('input[type="time"]')).toHaveLength(0)
    expect(lunes.text()).toContain('libre')
  })

  it('un error del servidor al guardar se enseña y se puede reintentar', async () => {
    vi.mocked(putSemanaTipo).mockRejectedValue(
      new ApiError(400, 'API 400', {
        status: 400,
        detail: 'Día 1: un tramo no puede empezar y acabar a la misma hora',
      }),
    )
    const wrapper = await montar()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('la misma hora')
    expect(wrapper.text()).not.toContain('Horario guardado')
  })
})

describe('HorarioView — una semana concreta (/horario/semana/:lunes)', () => {
  it('precarga el horario efectivo de esa semana y guarda con putSemana', async () => {
    const wrapper = await montar('/horario/semana/2026-07-06')

    expect(getHorarioSemana).toHaveBeenCalledWith('2026-07-06')
    expect(wrapper.text()).toContain('Solo la semana del 06/07/2026')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(putSemana).toHaveBeenCalledTimes(1)
    expect(vi.mocked(putSemana).mock.calls[0][0]).toBe('2026-07-06')
    expect(putSemanaTipo).not.toHaveBeenCalled()
    // El "vale de ahora en adelante" es de la semana tipo; aquí no aplica.
    expect(wrapper.text()).not.toContain('desde esta semana en adelante')
  })

  it('sin semana tipo previa (404): guía hacia /horario, no editor vacío', async () => {
    vi.mocked(getHorarioSemana).mockRejectedValue(
      noHay('No hay horario para esa semana: crea antes tu semana tipo'),
    )

    const wrapper = await montar('/horario/semana/2026-07-06')

    expect(wrapper.find('form').exists()).toBe(false)
    const enlace = wrapper.findAll('a').find((a) => a.text() === 'Crear tu horario')
    expect(enlace?.attributes('href')).toBe('/horario')
  })

  it('con un tramo de duración cero (misma hora) no deja guardar', async () => {
    const wrapper = await montar()

    const lunes = wrapper.findAll('.dia')[0]
    const horas = lunes.findAll('input[type="time"]')
    await horas[1].setValue('10:00') // entrada 10:00, salida 10:00

    expect(boton(wrapper, 'Guardar el horario').attributes('disabled')).toBeDefined()
    await wrapper.find('form').trigger('submit')
    expect(putSemanaTipo).not.toHaveBeenCalled()
  })

  it('una semana sellada (409) se explica al guardar', async () => {
    vi.mocked(putSemana).mockRejectedValue(
      new ApiError(409, 'API 409', {
        status: 409,
        detail: 'La semana del 2026-06-01 ya está sellada',
      }),
    )
    const wrapper = await montar('/horario/semana/2026-07-06')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('sellada')
  })

  it('al navegar entre modos con la MISMA instancia, el "guardado" viejo no se arrastra', async () => {
    const { wrapper, router } = await montarConRouter('/horario')

    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Horario guardado')

    await router.push('/horario/semana/2026-07-06')
    await flushPromises()

    // El router reutiliza el componente: la confirmación era de la semana
    // tipo, no de esta semana recién cargada y sin guardar.
    expect(wrapper.text()).not.toContain('Horario guardado')
    expect(wrapper.text()).toContain('Solo la semana del 06/07/2026')
  })

  it('una respuesta LENTA de la ruta anterior no pisa la semana en pantalla', async () => {
    // La semana concreta responde lenta (con 08:00); la semana tipo, rápida (10:00).
    let resuelveLenta!: (h: HorarioEfectivo) => void
    vi.mocked(getHorarioSemana).mockReturnValue(new Promise((res) => (resuelveLenta = res)))

    const { wrapper, router } = await montarConRouter('/horario/semana/2026-07-06')
    await router.push('/horario')
    await flushPromises()

    // La semana tipo ya está en pantalla...
    expect((wrapper.find('input[type="time"]').element as HTMLInputElement).value).toBe('10:00')

    // ...y la respuesta rezagada de la otra ruta llega tarde: no escribe nada.
    resuelveLenta({
      ...efectivoServidor,
      dias: [
        { tramos: [{ entrada: '08:00', salida: '16:00' }] },
        ...efectivoServidor.dias.slice(1),
      ],
    })
    await flushPromises()

    expect((wrapper.find('input[type="time"]').element as HTMLInputElement).value).toBe('10:00')
    // Y guardar guarda la semana tipo, nunca la semana rezagada.
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(putSemanaTipo).toHaveBeenCalledTimes(1)
    expect(putSemana).not.toHaveBeenCalled()
  })

  it('un lunes que no es lunes (o ni es fecha) no monta el editor', async () => {
    const wrapper = await montar('/horario/semana/2026-07-07')

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').text()).toContain('se identifica por su lunes')
    expect(getHorarioSemana).not.toHaveBeenCalled()

    const rota = await montar('/horario/semana/2026-02-30')
    expect(rota.find('form').exists()).toBe(false)
  })
})
