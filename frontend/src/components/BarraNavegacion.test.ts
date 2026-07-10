import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import BarraNavegacion from './BarraNavegacion.vue'
import { useAuthStore } from '../stores/auth'

vi.mock('../services/auth', () => ({
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
}))
vi.mock('../services/perfilUsuario', () => ({
  getPerfilUsuario: vi.fn(),
  putPerfilUsuario: vi.fn(),
}))

import { postLogin } from '../services/auth'

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/resumen', name: 'resumen', component: Stub },
      { path: '/libreta', name: 'libreta', component: Stub },
      { path: '/cuenta', name: 'cuenta', component: Stub },
    ],
  })
}

async function montar(ruta = '/') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = crearRouter()
  await router.push(ruta)
  const wrapper = mount(BarraNavegacion, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router }
}

async function conSesion() {
  vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-1', expiraEn: '2027-01-01T00:00:00Z', refreshToken: 'refresh-jwt-1', refreshExpiraEn: '2026-07-17T00:00:00Z' })
  const auth = useAuthStore()
  await auth.iniciarSesion('ana@example.com', 'superclave123')
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('BarraNavegacion', () => {
  it('sin sesión no se pinta: el flujo de entrada no la necesita', async () => {
    const { wrapper } = await montar()

    expect(wrapper.find('.barra').exists()).toBe(false)
  })

  it('con sesión enseña las tres pantallas del día a día', async () => {
    const { wrapper } = await montar()
    await conSesion()
    await flushPromises()

    const enlaces = wrapper.findAll('.pestana')
    expect(enlaces.map((e) => e.attributes('href'))).toEqual(['/resumen', '/libreta', '/cuenta'])
    expect(wrapper.text()).toContain('Lo tuyo')
    expect(wrapper.text()).toContain('Libreta')
    expect(wrapper.text()).toContain('Cuenta')
  })

  it('marca la pestaña de la pantalla actual', async () => {
    const { wrapper } = await montar('/libreta')
    await conSesion()
    await flushPromises()

    const activa = wrapper.findAll('.pestana').filter((e) => e.classes('router-link-active'))
    expect(activa).toHaveLength(1)
    expect(activa[0].attributes('href')).toBe('/libreta')
  })
})
