import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import type { ConvenioResumen, OcupacionResuelta, SalarioBase } from '../services/convenios'
import PerfilView from './PerfilView.vue'
import { useAuthStore } from '../stores/auth'

vi.mock('../services/convenios', () => ({
  getProvincias: vi.fn(),
  getConvenioParaTrabajador: vi.fn(),
  getPuestos: vi.fn(),
  getOcupacion: vi.fn(),
  postSalarioBase: vi.fn(),
}))
vi.mock('../services/auth', () => ({
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
}))

import {
  getConvenioParaTrabajador,
  getOcupacion,
  getProvincias,
  getPuestos,
  postSalarioBase,
} from '../services/convenios'
import { postLogin } from '../services/auth'

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/perfil', name: 'perfil', component: PerfilView },
      { path: '/login', name: 'login', component: Stub },
    ],
  })
}

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
  bajoSmi: false,
  smiMensual: 1221,
  minimoLegal: null,
  comparativaSmi: null,
  citas: [{ texto: 'Tabla salarial 2026', url: null }],
}

async function montar() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = crearRouter()
  await router.push('/perfil')
  const wrapper = mount(PerfilView, { global: { plugins: [pinia, router] } })
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
  it('sin sesión, la cabecera ofrece entrar (única salida visible) con vuelta aquí', async () => {
    const wrapper = await montar()

    const enlace = wrapper.find('.enlace-entrar')
    expect(enlace.exists()).toBe(true)
    expect(enlace.attributes('href')).toBe('/login?redirect=/perfil')
  })

  it('con sesión no hace falta el enlace de entrar: la barra inferior ya da salida', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-1', expiraEn: '2027-01-01T00:00:00Z' })
    const pinia = createPinia()
    setActivePinia(pinia)
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const router = crearRouter()
    await router.push('/perfil')
    const wrapper = mount(PerfilView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    expect(wrapper.find('.enlace-entrar').exists()).toBe(false)
  })

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
      bajoSmi: false,
      smiMensual: 1221,
      minimoLegal: null,
      comparativaSmi: null,
      citas: [],
    })
    const wrapper = await montar()
    await llegarAlConvenio(wrapper)

    await wrapper.find('#puesto').setValue('cocinero')
    await flushPromises()

    expect(wrapper.find('.aviso-unidad').exists()).toBe(true)
  })

  it('con la tabla bajo el SMI, la cifra grande es el suelo legal y el aviso explica la tabla', async () => {
    vi.mocked(postSalarioBase).mockResolvedValue({
      importe: 1086.31,
      unidad: 'EUR/mes',
      bajoSmi: true,
      smiMensual: 1221,
      minimoLegal: 1221,
      comparativaSmi: null,
      citas: [],
    })
    const wrapper = await montar()
    await llegarAlConvenio(wrapper)

    await wrapper.find('#puesto').setValue('cocinero')
    await flushPromises()

    // Lo que se enseña en grande es lo que por ley te corresponde como
    // mínimo, no la tabla superada (que iría a la calculadora infravalorada).
    expect(wrapper.find('.resultado .cifra').text()).toBe('1.221,00')
    const aviso = wrapper.find('.aviso-smi')
    expect(aviso.exists()).toBe(true)
    expect(aviso.text()).toContain('salario mínimo')
    expect(aviso.text()).toContain('1.086,31') // la tabla, ahora como contexto
    expect(aviso.attributes('role')).toBe('alert')
  })

  it('backend viejo (bajoSmi sin minimoLegal): la tabla se enseña pero el aviso NUNCA falta', async () => {
    // Despliegue por fases o respuesta cacheada: bajoSmi llega sin el suelo
    // calculado. El peor fallo posible sería tabla infra-SMI en grande y sin
    // aviso; este test lo clava.
    vi.mocked(postSalarioBase).mockResolvedValue({
      importe: 1086.31,
      unidad: 'EUR/mes',
      bajoSmi: true,
      smiMensual: 1221,
      minimoLegal: null,
      comparativaSmi: null,
      citas: [],
    })
    const wrapper = await montar()
    await llegarAlConvenio(wrapper)

    await wrapper.find('#puesto').setValue('cocinero')
    await flushPromises()

    expect(wrapper.find('.resultado .cifra').text()).toBe('1.086,31')
    const aviso = wrapper.find('.aviso-smi')
    expect(aviso.exists()).toBe(true)
    expect(aviso.text()).toContain('salario mínimo')
    expect(aviso.text()).toContain('1.221,00')
  })

  it('un mensual legal que PARECE bajo el SMI (15 pagas) se explica solo', async () => {
    // El caso Almería/Pontevedra que confundió hasta al dueño: 1.183,64 < 1.221
    // del titular, pero con 15 pagas el año cumple. La UI se adelanta a la duda.
    vi.mocked(postSalarioBase).mockResolvedValue({
      importe: 1183.64,
      unidad: 'EUR/mes',
      bajoSmi: false,
      smiMensual: 1221,
      minimoLegal: null,
      comparativaSmi: { mensualidades: 15, anualConvenio: 17754.6, smiAnual: 17094 },
      citas: [],
    })
    const wrapper = await montar()
    await llegarAlConvenio(wrapper)

    await wrapper.find('#puesto').setValue('cocinero')
    await flushPromises()

    const texto = wrapper.text()
    expect(texto).toContain('¿Te parece poco comparado con el SMI (1.221,00 € al mes)?')
    expect(texto).toContain('15 pagas al año')
    expect(texto).toContain('17.754,60')
    expect(texto).toContain('por años completos, no mes a mes')
    // No es una alerta: es contexto tranquilizador, sin bloque rojo.
    expect(wrapper.find('.aviso-smi').exists()).toBe(false)
  })

  it('no avisa del SMI cuando el salario lo alcanza', async () => {
    vi.mocked(postSalarioBase).mockResolvedValue(salarioMensual) // bajoSmi: false
    const wrapper = await montar()
    await llegarAlConvenio(wrapper)

    await wrapper.find('#puesto').setValue('cocinero')
    await flushPromises()

    expect(wrapper.find('.aviso-smi').exists()).toBe(false)
  })

  it('con una pregunta pendiente no calcula hasta que el usuario responde', async () => {
    // 1ª resolución: falta la clase de empresa. Al responder, el backend la pliega
    // en las dimensiones (re-resolución) y ya no queda pendiente.
    vi.mocked(getOcupacion)
      .mockResolvedValueOnce({
        dimensiones: { nivel: 'III' },
        pendientes: [{ dimension: 'claseEmpresa', valores: ['1ª', '2ª'] }],
        articulo: null,
      })
      .mockResolvedValueOnce({
        dimensiones: { nivel: 'III', claseEmpresa: '2ª' },
        pendientes: [],
        articulo: null,
      })
    const wrapper = await montar()
    await llegarAlConvenio(wrapper)

    await wrapper.find('#puesto').setValue('cocinero')
    await flushPromises()
    expect(postSalarioBase).not.toHaveBeenCalled()

    // El botón se muestra en cristiano ("Clase 2ª") pero envía el valor crudo ("2ª").
    const botonesPendiente = wrapper.findAll('.paso .opcion').filter((b) => b.text().includes('2ª'))
    expect(botonesPendiente[0].text()).toBe('Clase 2ª')
    await botonesPendiente[0].trigger('click')
    await flushPromises()

    // Se calcula con las dimensiones ya resueltas por el backend (no con la respuesta cruda).
    expect(postSalarioBase).toHaveBeenCalledWith(
      'madrid-hosteleria',
      { nivel: 'III', claseEmpresa: '2ª' },
      expect.any(String),
    )
    expect(wrapper.find('.resultado').exists()).toBe(true)
  })

  it('convenio condicional: encadena las preguntas del árbol hasta resolver el nivel y calcular', async () => {
    // Mapeo condicional (Jaén/Cataluña…): responder una pregunta revela la
    // siguiente; solo al final el árbol resuelve el nivel y se calcula el salario.
    vi.mocked(getOcupacion)
      .mockResolvedValueOnce({
        dimensiones: {},
        pendientes: [{ dimension: 'establecimiento', valores: ['hoteles', 'restaurantes'] }],
        articulo: 'Anexo',
      })
      .mockResolvedValueOnce({
        dimensiones: {},
        pendientes: [{ dimension: 'categoria', valores: ['1*', '2*'] }],
        articulo: 'Anexo',
      })
      .mockResolvedValueOnce({ dimensiones: { nivel: 'III' }, pendientes: [], articulo: 'Anexo' })

    const wrapper = await montar()
    await llegarAlConvenio(wrapper)
    await wrapper.find('#puesto').setValue('cocinero')
    await flushPromises()

    // 1ª pregunta del árbol: tipo de establecimiento.
    let opciones = wrapper.findAll('.paso .opcion').filter((b) => b.text().includes('Hoteles'))
    await opciones[0].trigger('click')
    await flushPromises()

    // Aún NO se ha calculado: falta la pregunta encadenada.
    expect(postSalarioBase).not.toHaveBeenCalled()

    // 2ª pregunta encadenada: categoría.
    opciones = wrapper.findAll('.paso .opcion').filter((b) => b.text().includes('1*'))
    await opciones[0].trigger('click')
    await flushPromises()

    // El árbol resolvió el nivel: se calcula SOLO con él, sin arrastrar los inputs
    // del árbol (tipo/categoría) que no son dimensiones de la tabla y darían 404.
    expect(postSalarioBase).toHaveBeenCalledWith(
      'madrid-hosteleria',
      { nivel: 'III' },
      expect.any(String),
    )
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
