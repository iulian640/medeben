import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import RegistroView from './RegistroView.vue'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
}))

import { postLogin, postRegistro } from '../services/auth'

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/login', name: 'login', component: Stub },
      { path: '/registro', name: 'registro', component: RegistroView },
      { path: '/cuenta', name: 'cuenta', component: Stub },
      // Rutas legales que sirve otra rama en paralelo; aquí solo hacen falta
      // para que RouterLink resuelva el path sin avisar.
      { path: '/privacidad', name: 'privacidad', component: Stub },
      { path: '/terminos', name: 'terminos', component: Stub },
    ],
  })
}

async function montar() {
  const router = crearRouter()
  await router.push('/registro')
  const wrapper = mount(RegistroView, { global: { plugins: [createPinia(), router] } })
  return { wrapper, router }
}

async function rellenar(
  wrapper: Awaited<ReturnType<typeof montar>>['wrapper'],
  email: string,
  password: string,
  repite: string,
) {
  await wrapper.find('input[type="email"]').setValue(email)
  const passwords = wrapper.findAll('input[type="password"]')
  await passwords[0].setValue(password)
  await passwords[1].setValue(repite)
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('RegistroView', () => {
  it('rechaza en cliente una contraseña de menos de 10 caracteres', async () => {
    const { wrapper } = await montar()

    await rellenar(wrapper, 'ana@example.com', 'corta', 'corta')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postRegistro).not.toHaveBeenCalled()
    expect(wrapper.text()).toMatch(/10 caracteres/)
  })

  it('rechaza en cliente si las contraseñas no coinciden', async () => {
    const { wrapper } = await montar()

    await rellenar(wrapper, 'ana@example.com', 'superclave123', 'superclave124')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postRegistro).not.toHaveBeenCalled()
    expect(wrapper.text()).toMatch(/no coinciden/i)
  })

  it('con datos válidos registra, entra y navega a la cuenta', async () => {
    vi.mocked(postRegistro).mockResolvedValue({ email: 'ana@example.com' })
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-1', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-1', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const { wrapper, router } = await montar()

    await rellenar(wrapper, 'ana@example.com', 'superclave123', 'superclave123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postRegistro).toHaveBeenCalledWith('ana@example.com', 'superclave123')
    expect(router.currentRoute.value.path).toBe('/cuenta')
  })
})

describe('RegistroView · información al interesado (RGPD art. 13)', () => {
  it('muestra responsable y finalidades en lenguaje llano antes de crear la cuenta', async () => {
    const { wrapper } = await montar()

    const texto = wrapper.text()
    expect(texto).toContain('Iulian Timofei')
    expect(texto).toMatch(/registrar tu jornada/i)
    expect(texto).toMatch(/lo que pudieran deberte/i)
  })

  it('enlaza a la política de privacidad completa por path', async () => {
    const { wrapper } = await montar()

    const enlace = wrapper.find('a[href="/privacidad"]')
    expect(enlace.exists()).toBe(true)
    expect(enlace.text()).toMatch(/política de privacidad/i)
  })

  it('ofrece los Términos bajo el botón, sin checkbox de consentimiento', async () => {
    const { wrapper } = await montar()

    const terminos = wrapper.find('a[href="/terminos"]')
    expect(terminos.exists()).toBe(true)
    expect(terminos.text()).toMatch(/términos/i)
    // La base jurídica es 6.1.b (ejecución del servicio), no consentimiento:
    // un checkbox de consentimiento sería incorrecto aquí.
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false)
  })

  it('el detalle "Más sobre tus datos" arranca plegado y abre/cierra', async () => {
    const { wrapper } = await montar()

    const toggle = wrapper.findAll('button').find((b) => b.text().includes('Más sobre tus datos'))
    expect(toggle).toBeDefined()

    const panel = wrapper.find('#detalle-rgpd')
    expect(panel.exists()).toBe(true)
    expect(toggle!.attributes('aria-expanded')).toBe('false')
    expect(panel.classes()).not.toContain('abierto')

    await toggle!.trigger('click')
    expect(toggle!.attributes('aria-expanded')).toBe('true')
    expect(panel.classes()).toContain('abierto')

    await toggle!.trigger('click')
    expect(toggle!.attributes('aria-expanded')).toBe('false')
  })

  it('el detalle cubre base jurídica, conservación y derechos (incl. AEPD)', async () => {
    const { wrapper } = await montar()

    const detalle = wrapper.find('#detalle-rgpd').text()
    expect(detalle).toMatch(/6\.1\.b/)
    expect(detalle).toMatch(/9\.2\.f/)
    expect(detalle).toMatch(/portabilidad/i)
    expect(detalle).toMatch(/AEPD/)
  })
})
