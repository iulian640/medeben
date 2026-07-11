import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { Pinia } from 'pinia'
import { useAuthStore } from '../stores/auth'
import { registrarReconciliacionSesion } from './reconciliacionSesion'

const Stub = { template: '<div />' }

function crearRouterPrueba() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/login', name: 'login', component: Stub },
      { path: '/cuenta', name: 'cuenta', component: Stub },
    ],
  })
}

let pinia: Pinia
let desregistrar: (() => void) | null = null

beforeEach(() => {
  pinia = createPinia()
  setActivePinia(pinia)
  vi.clearAllMocks()
})

afterEach(() => {
  // Cada test registra sus listeners sobre el MISMO window de jsdom: sin
  // desregistrar, los tests anteriores seguirían reaccionando a los eventos.
  desregistrar?.()
  desregistrar = null
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

/** Simula la vuelta desde el bfcache: pageshow con persisted=true. */
function despiertaDesdeBfcache() {
  window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted: true }))
}

describe('registrarReconciliacionSesion', () => {
  it('al volver del bfcache reconcilia la sesión con el candado compartido', async () => {
    const router = crearRouterPrueba()
    const auth = useAuthStore(pinia)
    const reconciliar = vi.spyOn(auth, 'reconciliar').mockResolvedValue(false)
    desregistrar = registrarReconciliacionSesion(router, pinia)

    despiertaDesdeBfcache()

    await vi.waitFor(() => expect(reconciliar).toHaveBeenCalledTimes(1))
  })

  it('un pageshow normal (carga fresca, sin bfcache) NO reconcilia: de eso ya se ocupa la restauración', async () => {
    const router = crearRouterPrueba()
    const auth = useAuthStore(pinia)
    const reconciliar = vi.spyOn(auth, 'reconciliar').mockResolvedValue(false)
    desregistrar = registrarReconciliacionSesion(router, pinia)

    window.dispatchEvent(new Event('pageshow'))
    await Promise.resolve()

    expect(reconciliar).not.toHaveBeenCalled()
  })

  it('al hacerse visible la pestaña reconcilia (vuelta de segundo plano)', async () => {
    const router = crearRouterPrueba()
    const auth = useAuthStore(pinia)
    const reconciliar = vi.spyOn(auth, 'reconciliar').mockResolvedValue(false)
    desregistrar = registrarReconciliacionSesion(router, pinia)

    // jsdom arranca con visibilityState 'visible': el evento basta.
    document.dispatchEvent(new Event('visibilitychange'))

    await vi.waitFor(() => expect(reconciliar).toHaveBeenCalledTimes(1))
  })

  it('pasar a OCULTA no reconcilia: solo importa el despertar', async () => {
    const router = crearRouterPrueba()
    const auth = useAuthStore(pinia)
    const reconciliar = vi.spyOn(auth, 'reconciliar').mockResolvedValue(false)
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    desregistrar = registrarReconciliacionSesion(router, pinia)

    document.dispatchEvent(new Event('visibilitychange'))
    await Promise.resolve()

    expect(reconciliar).not.toHaveBeenCalled()
  })

  it('si la reconciliación expulsa, navega a login con la vuelta preparada', async () => {
    const router = crearRouterPrueba()
    await router.push('/cuenta')
    const auth = useAuthStore(pinia)
    vi.spyOn(auth, 'reconciliar').mockResolvedValue(true)
    desregistrar = registrarReconciliacionSesion(router, pinia)

    despiertaDesdeBfcache()
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('login'))

    expect(router.currentRoute.value.query.redirect).toBe('/cuenta')
  })

  it('si la reconciliación expulsa pero YA estamos en login, no re-navega', async () => {
    const router = crearRouterPrueba()
    await router.push('/login')
    const auth = useAuthStore(pinia)
    vi.spyOn(auth, 'reconciliar').mockResolvedValue(true)
    const push = vi.spyOn(router, 'push')
    desregistrar = registrarReconciliacionSesion(router, pinia)

    despiertaDesdeBfcache()
    await vi.waitFor(() => expect(auth.reconciliar).toHaveBeenCalled())

    expect(push).not.toHaveBeenCalled()
  })

  it('si no hay expulsión, no navega a ninguna parte', async () => {
    const router = crearRouterPrueba()
    await router.push('/cuenta')
    const auth = useAuthStore(pinia)
    vi.spyOn(auth, 'reconciliar').mockResolvedValue(false)
    const push = vi.spyOn(router, 'push')
    desregistrar = registrarReconciliacionSesion(router, pinia)

    despiertaDesdeBfcache()
    await vi.waitFor(() => expect(auth.reconciliar).toHaveBeenCalled())

    expect(push).not.toHaveBeenCalled()
  })

  it('desregistrar retira los listeners: los eventos posteriores ya no reconcilian', async () => {
    const router = crearRouterPrueba()
    const auth = useAuthStore(pinia)
    const reconciliar = vi.spyOn(auth, 'reconciliar').mockResolvedValue(false)
    const parar = registrarReconciliacionSesion(router, pinia)

    parar()
    despiertaDesdeBfcache()
    document.dispatchEvent(new Event('visibilitychange'))
    await Promise.resolve()

    expect(reconciliar).not.toHaveBeenCalled()
  })
})
