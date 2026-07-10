import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { guardiaSesion } from './guardia'
import { instalarRecargaPorChunk } from './recargaPorChunk'

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
    {
      path: '/resumen',
      name: 'resumen',
      meta: { requiereSesion: true },
      component: () => import('../views/ResumenMesView.vue'),
    },
    {
      path: '/libreta',
      name: 'libreta',
      meta: { requiereSesion: true },
      component: () => import('../views/LibretaView.vue'),
    },
    {
      path: '/libreta/semana',
      name: 'libreta-semana',
      meta: { requiereSesion: true },
      component: () => import('../views/LibretaSemanaView.vue'),
    },
    {
      path: '/horario',
      name: 'horario',
      meta: { requiereSesion: true },
      component: () => import('../views/HorarioView.vue'),
    },
    {
      // El horario de UNA semana concreta ("me han cambiado el turno"): el
      // mismo editor, guardando una edición puntual sin tocar la semana tipo.
      path: '/horario/semana/:lunes',
      name: 'horario-semana',
      meta: { requiereSesion: true },
      component: () => import('../views/HorarioView.vue'),
    },
    {
      // Un día concreto (desde "Tu semana"): la misma pantalla de fichar,
      // cargando esa fecha. Para completar o corregir días pasados (D38:
      // 14 días de margen; después, rectificación tardía).
      path: '/libreta/dia/:fecha',
      name: 'libreta-dia',
      meta: { requiereSesion: true },
      component: () => import('../views/LibretaView.vue'),
    },
  ],
})

router.beforeEach(guardiaSesion)

// Recuperación ante deploys (chunks lazy con hash viejo): el cableado vive en
// recargaPorChunk.ts, testeado con un router de memoria; aquí solo se inyecta
// el mundo real (sessionStorage y location.assign).
instalarRecargaPorChunk(router, window.sessionStorage, (destino) =>
  window.location.assign(destino),
)

export default router
