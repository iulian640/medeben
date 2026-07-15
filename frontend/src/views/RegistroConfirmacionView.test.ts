import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import RegistroConfirmacionView from './RegistroConfirmacionView.vue'
import { useAuthStore } from '../stores/auth'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postReenviaVerificacion: vi.fn(),
}))

import { postReenviaVerificacion } from '../services/auth'

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/registro', name: 'registro', component: Stub },
      {
        path: '/registro/revisa-correo',
        name: 'registro-revisa-correo',
        component: RegistroConfirmacionView,
      },
      { path: '/login', name: 'login', component: Stub },
    ],
  })
}

/**
 * Siembra el store ANTES de montar: simula la navegación real desde
 * RegistroView, que deja el email recién registrado en memoria de la pestaña.
 */
async function montarConEmail(email: string | null, ruta = '/registro/revisa-correo') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.emailRecienRegistrado = email
  const router = crearRouter()
  await router.push(ruta)
  const wrapper = mount(RegistroConfirmacionView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router, auth }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('RegistroConfirmacionView', () => {
  it('sin email recién registrado (entrada directa o recarga), vuelve a /registro', async () => {
    const { router } = await montarConEmail(null)

    expect(router.currentRoute.value.name).toBe('registro')
  })

  it('con email recién registrado, lo muestra y menciona las 24h de validez', async () => {
    const { wrapper, router } = await montarConEmail('ana@example.com')

    expect(wrapper.text()).toContain('ana@example.com')
    expect(wrapper.text()).toMatch(/24 horas/i)
    expect(router.currentRoute.value.name).toBe('registro-revisa-correo')
  })

  it('ofrece un enlace para iniciar sesión (las cuentas sin verificar entran igual)', async () => {
    const { wrapper } = await montarConEmail('ana@example.com')

    expect(wrapper.find('a[href="/login"]').exists()).toBe(true)
  })

  it('el enlace "Iniciar sesión" conserva el ?redirect= del deep-link (no se pierde)', async () => {
    const { wrapper } = await montarConEmail(
      'ana@example.com',
      '/registro/revisa-correo?redirect=/libreta',
    )

    const login = wrapper.findAll('a').find((a) => a.text().includes('Iniciar sesión'))
    expect(login).toBeDefined()
    expect(login!.attributes('href')).toContain('redirect=')
    expect(login!.attributes('href')).toContain('libreta')
  })

  it('al desmontar limpia emailRecienRegistrado (no lo hereda el siguiente usuario)', async () => {
    const { wrapper, auth } = await montarConEmail('ana@example.com')
    expect(auth.emailRecienRegistrado).toBe('ana@example.com')

    wrapper.unmount()

    expect(auth.emailRecienRegistrado).toBeNull()
  })

  it('reenviar llama al servicio con el email y muestra confirmación genérica', async () => {
    vi.mocked(postReenviaVerificacion).mockResolvedValue(undefined)
    const { wrapper } = await montarConEmail('ana@example.com')

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(postReenviaVerificacion).toHaveBeenCalledWith('ana@example.com')
    expect(wrapper.text()).toMatch(/enviado/i)
  })

  it('reenviar muestra la MISMA confirmación aunque la llamada falle (sin revelar estados)', async () => {
    vi.mocked(postReenviaVerificacion).mockRejectedValue(new ApiError(500, 'API 500', null))
    const { wrapper } = await montarConEmail('ana@example.com')

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toMatch(/enviado/i)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
})
