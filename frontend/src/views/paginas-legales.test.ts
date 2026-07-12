import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import PrivacidadView from './PrivacidadView.vue'
import TerminosView from './TerminosView.vue'
import AvisoLegalView from './AvisoLegalView.vue'

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/privacidad', name: 'privacidad', component: PrivacidadView },
      { path: '/terminos', name: 'terminos', component: TerminosView },
      { path: '/aviso-legal', name: 'aviso-legal', component: AvisoLegalView },
      { path: '/borrar-cuenta', name: 'borrar-cuenta', component: Stub },
    ],
  })
}

function montar(componente: unknown) {
  const router = crearRouter()
  return mount(componente as never, { global: { plugins: [createPinia(), router] } })
}

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('PrivacidadView', () => {
  it('tiene un único h1 y transcribe los puntos clave de la política', () => {
    const wrapper = montar(PrivacidadView)

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.find('h1').text()).toMatch(/privacidad/i)
    expect(wrapper.text()).toContain('iuliantim21@gmail.com')
    expect(wrapper.text()).toMatch(/Hetzner/i)
    expect(wrapper.text()).toMatch(/categoría especial/i)
    expect(wrapper.text()).toMatch(/AEPD/i)
  })

  it('enlaza a /borrar-cuenta y a la web de la AEPD', () => {
    const wrapper = montar(PrivacidadView)
    const hrefs = wrapper.findAll('a').map((a) => a.attributes('href'))

    expect(hrefs).toContain('/borrar-cuenta')
    expect(hrefs.some((h) => h?.includes('aepd.es'))).toBe(true)
  })
})

describe('TerminosView', () => {
  it('transcribe los términos y marca la gratuidad', () => {
    const wrapper = montar(TerminosView)

    expect(wrapper.find('h1').text()).toMatch(/términos/i)
    expect(wrapper.text()).toMatch(/gratuita/i)
    expect(wrapper.text()).toMatch(/AGPL-3\.0/i)
    expect(wrapper.text()).toMatch(/orientativos/i)
  })
})

describe('AvisoLegalView', () => {
  it('transcribe el aviso legal e identifica al titular', () => {
    const wrapper = montar(AvisoLegalView)

    expect(wrapper.find('h1').text()).toMatch(/aviso legal/i)
    expect(wrapper.text()).toContain('Iulian Timofei')
    expect(wrapper.text()).toMatch(/legislación española/i)
  })

  it('enlaza a la política de privacidad', () => {
    const wrapper = montar(AvisoLegalView)
    const hrefs = wrapper.findAll('a').map((a) => a.attributes('href'))

    expect(hrefs).toContain('/privacidad')
  })
})

describe('enlaces legales cruzados', () => {
  it('cada página ofrece navegar a las otras páginas legales', () => {
    for (const componente of [PrivacidadView, TerminosView, AvisoLegalView]) {
      const wrapper = montar(componente)
      const hrefs = wrapper.findAll('a').map((a) => a.attributes('href'))
      expect(hrefs).toContain('/privacidad')
      expect(hrefs).toContain('/terminos')
      expect(hrefs).toContain('/aviso-legal')
    }
  })
})
