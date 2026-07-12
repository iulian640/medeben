import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import BorrarCuentaView from './BorrarCuentaView.vue'

// Misma técnica que LoginView/CuentaView: se doblan los endpoints, no el store.
// Así el test recorre la lógica REAL de auth (iniciarSesion → borrarCuenta) y
// solo se corta la red.
vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postLogout: vi.fn(),
  deleteCuenta: vi.fn(),
}))

import { deleteCuenta, postLogin, postLogout } from '../services/auth'

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/privacidad', name: 'privacidad', component: Stub },
      { path: '/terminos', name: 'terminos', component: Stub },
      { path: '/aviso-legal', name: 'aviso-legal', component: Stub },
      { path: '/borrar-cuenta', name: 'borrar-cuenta', component: BorrarCuentaView },
    ],
  })
}

async function montar() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = crearRouter()
  await router.push('/borrar-cuenta')
  const wrapper = mount(BorrarCuentaView, { global: { plugins: [pinia, router] } })
  return { wrapper, router }
}

async function rellenar(wrapper: Awaited<ReturnType<typeof montar>>['wrapper'], email: string, password: string) {
  await wrapper.find('#email-borrado').setValue(email)
  await wrapper.find('#password-borrado').setValue(password)
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(postLogout).mockResolvedValue(undefined)
})

describe('BorrarCuentaView', () => {
  it('explica qué se borra y avisa de descargar el PDF antes', async () => {
    const { wrapper } = await montar()
    const texto = wrapper.text()

    expect(texto).toMatch(/cuenta/i)
    expect(texto).toMatch(/perfil/i)
    expect(texto).toMatch(/fichajes/i)
    expect(texto).toMatch(/ausencias/i)
    expect(texto).toMatch(/informes?/i)
    expect(texto).toMatch(/descarga.*pdf/i)
  })

  it('sin email o contraseña no continúa ni llama a la API', async () => {
    const { wrapper } = await montar()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postLogin).not.toHaveBeenCalled()
    expect(wrapper.find('button.boton-borrar').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').text()).toMatch(/email|contraseña/i)
  })

  it('con un email inválido no continúa', async () => {
    const { wrapper } = await montar()

    await rellenar(wrapper, 'no-es-un-email', 'superclave123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postLogin).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toMatch(/email/i)
    expect(wrapper.find('button.boton-borrar').exists()).toBe(false)
  })

  it('continuar con datos válidos muestra la confirmación final y todavía no borra', async () => {
    const { wrapper } = await montar()

    await rellenar(wrapper, 'ana@example.com', 'superclave123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('button.boton-borrar').exists()).toBe(true)
    const alertas = wrapper.findAll('[role="alert"]').map((a) => a.text())
    expect(alertas.some((t) => /no hay vuelta atrás|para siempre|definitiv/i.test(t))).toBe(true)
    expect(postLogin).not.toHaveBeenCalled()
    expect(deleteCuenta).not.toHaveBeenCalled()
  })

  it('confirmar hace login y DELETE con la contraseña, y muestra el resultado', async () => {
    vi.mocked(postLogin).mockResolvedValue({
      token: 'jwt-1',
      expiraEn: '2026-07-13T00:00:00Z',
      refreshToken: 'refresh-1',
      refreshExpiraEn: '2026-07-20T00:00:00Z',
    })
    vi.mocked(deleteCuenta).mockResolvedValue(undefined)
    const { wrapper } = await montar()

    await rellenar(wrapper, 'ana@example.com', 'superclave123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await wrapper.find('button.boton-borrar').trigger('click')
    await flushPromises()

    expect(postLogin).toHaveBeenCalledWith('ana@example.com', 'superclave123')
    expect(deleteCuenta).toHaveBeenCalledWith('superclave123')
    expect(wrapper.text()).toMatch(/se han borrado|cuenta.*borrad/i)
  })

  it('credenciales incorrectas: mensaje en castellano y no se borra', async () => {
    vi.mocked(postLogin).mockRejectedValue(
      new ApiError(401, 'API 401', { status: 401, detail: 'Email o contraseña incorrectos' }),
    )
    const { wrapper } = await montar()

    await rellenar(wrapper, 'ana@example.com', 'claveMala123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await wrapper.find('button.boton-borrar').trigger('click')
    await flushPromises()

    expect(deleteCuenta).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Email o contraseña incorrectos')
  })

  it('cancelar la confirmación vuelve atrás sin borrar nada', async () => {
    const { wrapper } = await montar()

    await rellenar(wrapper, 'ana@example.com', 'superclave123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await wrapper.find('button.boton-cancelar').trigger('click')
    await flushPromises()

    expect(wrapper.find('button.boton-borrar').exists()).toBe(false)
    expect(postLogin).not.toHaveBeenCalled()
    expect(deleteCuenta).not.toHaveBeenCalled()
  })

  it('ofrece la vía alternativa por email (iuliantim21@gmail.com, plazo de un mes)', async () => {
    const { wrapper } = await montar()

    const mailto = wrapper
      .findAll('a')
      .map((a) => a.attributes('href') ?? '')
      .find((h) => h.startsWith('mailto:'))
    expect(mailto).toBeDefined()
    expect(mailto).toContain('iuliantim21@gmail.com')
    expect(wrapper.text()).toMatch(/un mes|1 mes/i)
  })
})
