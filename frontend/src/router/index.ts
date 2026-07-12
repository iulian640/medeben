import { createRouter, createWebHistory } from 'vue-router'
import { guardiaSesion } from './guardia'
import { instalarRecargaPorChunk } from './recargaPorChunk'
import { rutas } from './rutas'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: rutas,
})

router.beforeEach(guardiaSesion)

// Recuperación ante deploys (chunks lazy con hash viejo): el cableado vive en
// recargaPorChunk.ts, testeado con un router de memoria; aquí solo se inyecta
// el mundo real (sessionStorage y location.assign).
instalarRecargaPorChunk(router, window.sessionStorage, (destino) =>
  window.location.assign(destino),
)

export default router
