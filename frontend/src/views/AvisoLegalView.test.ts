import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import AvisoLegalView from './AvisoLegalView.vue'

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/privacidad', name: 'privacidad', component: Stub },
      { path: '/terminos', name: 'terminos', component: Stub },
      { path: '/aviso-legal', name: 'aviso-legal', component: AvisoLegalView },
    ],
  })
}

function montar() {
  const router = crearRouter()
  return mount(AvisoLegalView, { global: { plugins: [createPinia(), router] } })
}

beforeEach(() => {
  setActivePinia(createPinia())
})

// La identificación sensible (NIF/domicilio) se inyecta por entorno: cada test
// fija el entorno que necesita y lo deshace al terminar para no contaminar a
// los demás (Vitest carga .env.local, con el valor real, si existe).
afterEach(() => {
  vi.unstubAllEnvs()
})

describe('AvisoLegalView · identificación inyectada por entorno', () => {
  it('con NIF y domicilio en el entorno, los muestra', () => {
    vi.stubEnv('VITE_RESPONSABLE_NIF', 'Y0000000X')
    vi.stubEnv('VITE_RESPONSABLE_DOMICILIO', 'C/ Ejemplo 1, 00000 Ciudad')

    const wrapper = montar()

    expect(wrapper.text()).toContain('Y0000000X')
    expect(wrapper.text()).toContain('C/ Ejemplo 1, 00000 Ciudad')
  })

  it('sin las variables de entorno, muestra el texto de reserva y NO rompe', () => {
    // Cadena vacía = variable no inyectada (así se comporta el repo público / CI).
    vi.stubEnv('VITE_RESPONSABLE_NIF', '')
    vi.stubEnv('VITE_RESPONSABLE_DOMICILIO', '')

    const wrapper = montar()

    expect(wrapper.find('h1').text()).toMatch(/aviso legal/i)
    expect(wrapper.text()).toMatch(/medeben\.net\/aviso-legal/i)
    // Los DOS campos sensibles (NIF y domicilio) caen al mismo texto de reserva:
    // aparece dos veces, señal de que ninguno filtra un valor cuando faltan.
    // (No se escriben aquí los valores reales: viven solo en .env.local, gitignored.)
    const reservas = wrapper.text().match(/no se incluye en este repositorio público/gi)
    expect(reservas).toHaveLength(2)
  })

  it('el titular y el correo son estáticos: se ven siempre (son públicos)', () => {
    vi.stubEnv('VITE_RESPONSABLE_NIF', '')
    vi.stubEnv('VITE_RESPONSABLE_DOMICILIO', '')

    const wrapper = montar()

    expect(wrapper.text()).toContain('Iulian Timofei')
    expect(wrapper.text()).toContain('iuliantim21@gmail.com')
  })

  it('menciona las donaciones (Ko-fi) como actividad económica', () => {
    const wrapper = montar()
    expect(wrapper.text()).toMatch(/ko-fi/i)
    expect(wrapper.text()).toMatch(/donaci/i)
  })
})
