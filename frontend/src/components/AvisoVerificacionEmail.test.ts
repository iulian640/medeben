import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AvisoVerificacionEmail from './AvisoVerificacionEmail.vue'
import { useAuthStore } from '../stores/auth'

vi.mock('../services/auth', () => ({
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
  // El store de auth también puede revocar/renovar en segundo plano (issue
  // #229): sin estos stubs, cualquier camino que los toque revienta el mock.
  postLogout: vi.fn().mockResolvedValue(undefined),
  postRefresh: vi.fn(),
  getMe: vi.fn(),
  postReenviaVerificacion: vi.fn(),
}))

import { getMe, postLogin, postReenviaVerificacion } from '../services/auth'

async function montar() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(AvisoVerificacionEmail, { global: { plugins: [pinia] } })
  await flushPromises()
  return { wrapper }
}

async function conSesion(verificado: boolean) {
  vi.mocked(postLogin).mockResolvedValue({
    token: 'jwt-1',
    expiraEn: '2027-01-01T00:00:00Z',
    refreshToken: 'refresh-jwt-1',
    refreshExpiraEn: '2027-01-08T00:00:00Z',
  })
  vi.mocked(getMe).mockResolvedValue({ email: 'ana@example.com', emailVerificado: verificado })
  const auth = useAuthStore()
  await auth.iniciarSesion('ana@example.com', 'superclave123')
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('AvisoVerificacionEmail', () => {
  it('sin sesión no se muestra (y ni siquiera pide el estado a /me)', async () => {
    const { wrapper } = await montar()

    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(getMe).not.toHaveBeenCalled()
  })

  it('con sesión y SIN verificar, se muestra', async () => {
    const { wrapper } = await montar()
    await conSesion(false)
    await flushPromises()

    expect(wrapper.find('[role="status"]').exists()).toBe(true)
    expect(wrapper.text()).toMatch(/confirma tu correo/i)
  })

  it('con sesión y YA verificada, no se muestra', async () => {
    const { wrapper } = await montar()
    await conSesion(true)
    await flushPromises()

    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('es accesible: role="status" con aria-live="polite"', async () => {
    const { wrapper } = await montar()
    await conSesion(false)
    await flushPromises()

    const aviso = wrapper.find('[role="status"]')
    expect(aviso.attributes('aria-live')).toBe('polite')
  })

  it('reenviar llama al servicio con el email de la sesión y muestra confirmación genérica', async () => {
    vi.mocked(postReenviaVerificacion).mockResolvedValue(undefined)
    const { wrapper } = await montar()
    await conSesion(false)
    await flushPromises()

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(postReenviaVerificacion).toHaveBeenCalledWith('ana@example.com')
    expect(wrapper.text()).toMatch(/enviado/i)
  })
})
