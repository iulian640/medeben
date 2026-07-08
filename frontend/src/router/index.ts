import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { guardiaSesion } from './guardia'
import {
  debeRecargarPorChunk,
  esErrorDeCargaDeChunk,
  limpiarMarcaRecarga,
} from '../lib/recargaChunks'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/perfil',
      name: 'perfil',
      // Lazy: la Home carga ligera; el flujo de perfil se trae al navegar.
      component: () => import('../views/PerfilView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/registro',
      name: 'registro',
      component: () => import('../views/RegistroView.vue'),
    },
    {
      path: '/cuenta',
      name: 'cuenta',
      meta: { requiereSesion: true },
      component: () => import('../views/CuentaView.vue'),
    },
  ],
})

router.beforeEach(guardiaSesion)

// Tras un deploy, esta pestaña puede seguir con el JS viejo pidiendo chunks
// (rutas lazy) que ya no existen. Se recarga UNA vez hacia la ruta destino
// para traer el index.html nuevo; la marca anti-bucle evita ciclar si el
// fallo persiste tras recargar.
router.onError((error, to) => {
  if (esErrorDeCargaDeChunk(error) && debeRecargarPorChunk(window.sessionStorage)) {
    window.location.assign(to.fullPath)
  }
})

// Navegación completada: si hubo recarga por chunk, ya funcionó. Se retira la
// marca para que un deploy futuro pueda volver a recuperarse igual.
router.afterEach(() => {
  limpiarMarcaRecarga(window.sessionStorage)
})

export default router
