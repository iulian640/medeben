import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import HomeView from './HomeView.vue'
import { useAuthStore } from '../stores/auth'

vi.mock('../services/auth', () => ({
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
}))

import { postLogin } from '../services/auth'

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: HomeView },
      { path: '/perfil', name: 'perfil', component: Stub },
      { path: '/login', name: 'login', component: Stub },
      { path: '/cuenta', name: 'cuenta', component: Stub },
      { path: '/libreta', name: 'libreta', component: Stub },
    ],
  })
}

let pinia: Pinia

beforeEach(() => {
  pinia = createPinia()
  setActivePinia(pinia)
  vi.clearAllMocks()
})

describe('HomeView', () => {
  it('sin sesión invita a entrar y no enseña la libreta', () => {
    const wrapper = mount(HomeView, { global: { plugins: [pinia, crearRouter()] } })

    expect(wrapper.text()).toContain('Entra o crea tu cuenta')
    expect(wrapper.text()).not.toContain('Tu libreta')
  })

  it('con sesión enseña el acceso a la libreta y a la cuenta', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-1', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-1', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const wrapper = mount(HomeView, { global: { plugins: [pinia, crearRouter()] } })

    const enlaceLibreta = wrapper
      .findAll('a')
      .find((a) => a.text().includes('Tu libreta: ficha tu jornada'))
    expect(enlaceLibreta?.attributes('href')).toBe('/libreta')
    expect(wrapper.text()).toContain('ana@example.com')
  })
})
