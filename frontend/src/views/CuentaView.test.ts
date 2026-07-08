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
}))

import { getPerfilUsuario, putPerfilUsuario } from '../services/perfilUsuario'
import { getProvincias, getPuestos } from '../services/convenios'

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
    ],
  })
}

async function montar() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.token = 'jwt-1'
  auth.email = 'ana@example.com'
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

  it('cerrar sesión limpia la sesión y vuelve a la portada', async () => {
    vi.mocked(getPerfilUsuario).mockResolvedValue(perfilServidor)
    const { wrapper, router, auth } = await montar()

    await wrapper.find('button.salir').trigger('click')
    await flushPromises()

    expect(auth.autenticado).toBe(false)
    expect(router.currentRoute.value.path).toBe('/')
  })
})
