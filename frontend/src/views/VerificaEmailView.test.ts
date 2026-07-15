import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import VerificaEmailView from './VerificaEmailView.vue'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postVerificaEmail: vi.fn(),
  postReenviaVerificacion: vi.fn(),
}))

import { postReenviaVerificacion, postVerificaEmail } from '../services/auth'

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/verifica-email', name: 'verifica-email', component: VerificaEmailView },
      { path: '/login', name: 'login', component: Stub },
    ],
  })
}

async function montar(query = '?token=el-token') {
  setActivePinia(createPinia())
  const router = crearRouter()
  await router.push(`/verifica-email${query}`)
  const wrapper = mount(VerificaEmailView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('VerificaEmailView', () => {
  it('lee el token de la query y llama a la API UNA sola vez al montar', async () => {
    vi.mocked(postVerificaEmail).mockResolvedValue(undefined)

    await montar('?token=el-token')

    expect(postVerificaEmail).toHaveBeenCalledTimes(1)
    expect(postVerificaEmail).toHaveBeenCalledWith('el-token')
  })

  it('limpia el token de la URL tras leerlo (no queda en el history de la tablet)', async () => {
    vi.mocked(postVerificaEmail).mockResolvedValue(undefined)

    const { router } = await montar('?token=el-token')

    // El token se canjeó UNA vez, pero ya no está en la barra de direcciones
    // (ni en el history ni disponible para el Referer): mismo path, sin query.
    expect(router.currentRoute.value.path).toBe('/verifica-email')
    expect(router.currentRoute.value.query.token).toBeUndefined()
    expect(postVerificaEmail).toHaveBeenCalledTimes(1)
  })

  it('éxito: marca la cuenta como verificada y ofrece iniciar sesión', async () => {
    vi.mocked(postVerificaEmail).mockResolvedValue(undefined)

    const { wrapper } = await montar()

    expect(wrapper.text()).toMatch(/verificada/i)
    expect(wrapper.find('a[href="/login"]').exists()).toBe(true)
  })

  it('error (400): muestra el mensaje del backend y ofrece pedir un enlace nuevo', async () => {
    vi.mocked(postVerificaEmail).mockRejectedValue(
      new ApiError(400, 'API 400', {
        status: 400,
        detail: 'El enlace de verificación no es válido o ha caducado',
      }),
    )

    const { wrapper } = await montar()

    expect(wrapper.find('[role="alert"]').text()).toBe(
      'El enlace de verificación no es válido o ha caducado',
    )
    expect(wrapper.find('form').exists()).toBe(true)
  })

  it('sin token en la URL, muestra error SIN llamar a la API', async () => {
    await montar('')

    expect(postVerificaEmail).not.toHaveBeenCalled()
  })

  it('desde el error, reenviar pide el email (sin sesión) y muestra confirmación genérica', async () => {
    vi.mocked(postVerificaEmail).mockRejectedValue(
      new ApiError(400, 'API 400', { status: 400, detail: 'El enlace ha caducado' }),
    )
    vi.mocked(postReenviaVerificacion).mockResolvedValue(undefined)

    const { wrapper } = await montar()

    await wrapper.find('#email-reenvio').setValue('ana@example.com')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postReenviaVerificacion).toHaveBeenCalledWith('ana@example.com')
    expect(wrapper.text()).toMatch(/enviado/i)
  })

  it('reenviar sin escribir un email válido no llama a la API', async () => {
    vi.mocked(postVerificaEmail).mockRejectedValue(
      new ApiError(400, 'API 400', { status: 400, detail: 'El enlace ha caducado' }),
    )

    const { wrapper } = await montar()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postReenviaVerificacion).not.toHaveBeenCalled()
  })
})
