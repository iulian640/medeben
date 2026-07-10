import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import type { PerfilGuardado } from '../services/perfilUsuario'
import CuentaView from './CuentaView.vue'
import { useAuthStore } from '../stores/auth'

vi.mock('../services/perfilUsuario', () => ({
  getPerfilUsuario: vi.fn(),
  putPerfilUsuario: vi.fn(),
}))
vi.mock('../services/convenios', () => ({
  getProvincias: vi.fn(),
  getPuestos: vi.fn(),
  getConvenioParaTrabajador: vi.fn(),
  getOcupacion: vi.fn(),
}))
vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
  deleteCuenta: vi.fn(),
}))

import { getPerfilUsuario, putPerfilUsuario } from '../services/perfilUsuario'
import { getProvincias, getPuestos } from '../services/convenios'
import { deleteCuenta, postLogin } from '../services/auth'

const perfilServidor: PerfilGuardado = {
  provincia: 'Madrid',
  subsector: 'hosteleria',
  convenioId: 'madrid-hosteleria',
  puestoId: 'cocinero',
  dimensiones: { nivel: 'III' },
  salarioBaseMensual: 1500,
  plusesAnuales: 0,
}

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/login', name: 'login', component: Stub },
      { path: '/cuenta', name: 'cuenta', component: CuentaView },
      { path: '/horario', name: 'horario', component: Stub },
    ],
  })
}

async function montar() {
  const pinia = createPinia()
  setActivePinia(pinia)
  // El token es de solo lectura: la sesión de prueba se abre por la puerta de verdad.
  vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-1', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-1', refreshExpiraEn: '2026-07-17T00:00:00Z' })
  const auth = useAuthStore()
  await auth.iniciarSesion('ana@example.com', 'superclave123')
  const router = crearRouter()
  await router.push('/cuenta')
  const wrapper = mount(CuentaView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router, auth }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getProvincias).mockResolvedValue(['Madrid', 'Cuenca'])
  vi.mocked(getPuestos).mockResolvedValue([
    { id: 'cocinero', etiqueta: 'Cocinero/a' },
    { id: 'camarero', etiqueta: 'Camarero/a' },
  ])
})

describe('CuentaView', () => {
  it('carga el perfil del servidor y rellena el formulario', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)

    const { wrapper } = await montar()

    expect((wrapper.find('#provincia').element as HTMLSelectElement).value).toBe('Madrid')
    expect((wrapper.find('#puesto').element as HTMLSelectElement).value).toBe('cocinero')
    expect((wrapper.find('#salario').element as HTMLInputElement).value).toBe('1500')
    expect(wrapper.text()).toContain('ana@example.com')
  })

  it('sin perfil todavía (404) enseña el formulario vacío con aviso, sin error', async () => {
    vi.mocked(getPerfilUsuario).mockRejectedValue(new ApiError(404, 'API 404', null))

    const { wrapper } = await montar()

    expect(wrapper.text()).toMatch(/todavía no has guardado/i)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('guardar manda el PUT y confirma', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    vi.mocked(putPerfilUsuario).mockResolvedValue(perfilServidor)
    const { wrapper } = await montar()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(putPerfilUsuario).toHaveBeenCalledWith({
      provincia: 'Madrid',
      subsector: 'hosteleria',
      puestoId: 'cocinero',
      dimensiones: { nivel: 'III' },
      salarioBaseMensual: 1500,
      plusesAnuales: 0,
    })
    expect(wrapper.text()).toMatch(/guardado/i)
  })

  it('editar un campo tras guardar oculta la confirmación', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    vi.mocked(putPerfilUsuario).mockResolvedValue(perfilServidor)
    const { wrapper } = await montar()

    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toMatch(/perfil guardado\./i)

    await wrapper.find('#salario').setValue('1600')
    await flushPromises()

    expect(wrapper.text()).not.toMatch(/perfil guardado\./i)
  })

  it('la cuenta ofrece editar tu horario (acceso directo al editor)', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const { wrapper } = await montar()

    const enlace = wrapper.findAll('a').find((a) => a.text() === 'Editar tu horario')
    expect(enlace?.attributes('href')).toBe('/horario')
  })

  it('cerrar sesión limpia la sesión y vuelve a la portada', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const { wrapper, router, auth } = await montar()

    await wrapper.find('button.salir').trigger('click')
    await flushPromises()

    expect(auth.autenticado).toBe(false)
    expect(router.currentRoute.value.path).toBe('/')
  })

  // --- Borrado de cuenta (RGPD art. 17): doble confirmación ---

  it('la zona de borrado avisa de descargar los PDF y NO enseña la contraseña de primeras', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const { wrapper } = await montar()

    expect(wrapper.text()).toMatch(/borrar tu cuenta/i)
    expect(wrapper.text()).toMatch(/descarga.*pdf/i)
    // Paso 1 todavía: sin confirmación no hay campo de contraseña.
    expect(wrapper.find('#password-borrado').exists()).toBe(false)
  })

  it('pedir el borrado abre el segundo paso: aviso final + contraseña', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const { wrapper } = await montar()

    await wrapper.find('button.boton-abrir-borrado').trigger('click')

    expect(wrapper.find('#password-borrado').exists()).toBe(true)
    expect(wrapper.text()).toMatch(/no hay vuelta atrás/i)
  })

  it('cancelar cierra el segundo paso sin borrar nada', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const { wrapper } = await montar()

    await wrapper.find('button.boton-abrir-borrado').trigger('click')
    await wrapper.find('button.boton-cancelar-borrado').trigger('click')

    expect(wrapper.find('#password-borrado').exists()).toBe(false)
    expect(deleteCuenta).not.toHaveBeenCalled()
  })

  it('confirmar con contraseña borra la cuenta, cierra la sesión y va a la portada', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    vi.mocked(deleteCuenta).mockResolvedValue(undefined)
    const { wrapper, router, auth } = await montar()

    await wrapper.find('button.boton-abrir-borrado').trigger('click')
    await wrapper.find('#password-borrado').setValue('superclave123')
    await wrapper.find('form.form-borrado').trigger('submit')
    await flushPromises()

    expect(deleteCuenta).toHaveBeenCalledWith('superclave123')
    expect(auth.autenticado).toBe(false)
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('contraseña incorrecta (403): el error se ve en el panel y la sesión sigue viva', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    vi.mocked(deleteCuenta).mockRejectedValue(
      new ApiError(403, 'API 403', { status: 403, detail: 'La contraseña no es correcta' }),
    )
    const { wrapper, router, auth } = await montar()

    await wrapper.find('button.boton-abrir-borrado').trigger('click')
    await wrapper.find('#password-borrado').setValue('laMala1234')
    await wrapper.find('form.form-borrado').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('La contraseña no es correcta')
    expect(auth.autenticado).toBe(true)
    expect(router.currentRoute.value.path).toBe('/cuenta')
  })

  it('el botón de confirmar exige contraseña: vacío no dispara nada', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const { wrapper } = await montar()

    await wrapper.find('button.boton-abrir-borrado').trigger('click')
    await wrapper.find('form.form-borrado').trigger('submit')
    await flushPromises()

    expect(deleteCuenta).not.toHaveBeenCalled()
  })

  it('HIGH review: cancelar un intento fallido y reabrir NO enseña el error viejo', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    vi.mocked(deleteCuenta).mockRejectedValue(
      new ApiError(403, 'API 403', { status: 403, detail: 'La contraseña no es correcta' }),
    )
    const { wrapper } = await montar()

    await wrapper.find('button.boton-abrir-borrado').trigger('click')
    await wrapper.find('#password-borrado').setValue('laMala1234')
    await wrapper.find('form.form-borrado').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('La contraseña no es correcta')

    await wrapper.find('button.boton-cancelar-borrado').trigger('click')
    await wrapper.find('button.boton-abrir-borrado').trigger('click')

    expect(wrapper.text()).not.toContain('La contraseña no es correcta')
  })

  it('HIGH review: la advertencia final se anuncia a lectores de pantalla (role=alert)', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const { wrapper } = await montar()

    await wrapper.find('button.boton-abrir-borrado').trigger('click')

    const alertas = wrapper.findAll('[role="alert"]').map((a) => a.text())
    expect(alertas.some((t) => /no hay vuelta atrás/i.test(t))).toBe(true)
  })

  it('HIGH review: abrir mueve el foco a la contraseña; cancelar lo devuelve al botón', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const pinia = createPinia()
    setActivePinia(pinia)
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-1', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-1', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const router = crearRouter()
    await router.push('/cuenta')
    // attachTo: el foco real solo existe con el componente en el documento.
    const wrapper = mount(CuentaView, {
      global: { plugins: [pinia, router] },
      attachTo: document.body,
    })
    await flushPromises()

    await wrapper.find('button.boton-abrir-borrado').trigger('click')
    await flushPromises()
    expect(document.activeElement?.id).toBe('password-borrado')

    await wrapper.find('button.boton-cancelar-borrado').trigger('click')
    await flushPromises()
    expect(document.activeElement?.className).toContain('boton-abrir-borrado')

    wrapper.unmount()
  })
})
