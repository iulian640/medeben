import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { guardiaSesion } from './guardia'

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

export default router
