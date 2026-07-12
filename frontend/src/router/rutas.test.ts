import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { rutas } from './rutas'
import { guardiaSesion } from './guardia'

/**
 * Las 4 páginas legales tienen que ser accesibles SIN sesión: la ley y Google
 * Play las exigen públicas, y /borrar-cuenta debe funcionar aunque el usuario
 * ya no tenga la app. Se prueba a través del router real + la guardia (no un
 * mock): si algún día alguien les colara `meta.requiereSesion`, esto se pone rojo.
 */
const RUTAS_LEGALES = ['/privacidad', '/terminos', '/aviso-legal', '/borrar-cuenta']

function crearRouter(): Router {
  const router = createRouter({ history: createMemoryHistory(), routes: rutas })
  router.beforeEach(guardiaSesion)
  return router
}

beforeEach(() => {
  // Sin sesión persistida (localStorage hermético del setup): la restauración de
  // la guardia devuelve false sin tocar la red.
  setActivePinia(createPinia())
})

describe('rutas legales públicas', () => {
  it.each(RUTAS_LEGALES)('%s existe y no exige sesión', (path) => {
    const resuelta = crearRouter().resolve(path)
    expect(resuelta.matched.length).toBeGreaterThan(0)
    expect(resuelta.meta.requiereSesion).toBeUndefined()
  })

  it.each(RUTAS_LEGALES)('la guardia deja pasar a %s sin sesión (no redirige a login)', async (path) => {
    const router = crearRouter()

    await router.push(path)
    await router.isReady()

    expect(router.currentRoute.value.path).toBe(path)
    expect(router.currentRoute.value.name).not.toBe('login')
  })
})
