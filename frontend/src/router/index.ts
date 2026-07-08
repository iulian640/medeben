import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

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
  ],
})

export default router
