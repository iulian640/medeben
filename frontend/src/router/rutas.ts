import type { RouteRecordRaw } from 'vue-router'
import HomeView from '../views/HomeView.vue'

/**
 * Tabla de rutas SIN efectos secundarios: separada de index.ts (que instancia el
 * router y cablea la recuperación por chunk sobre `window`) para poder probarla
 * con un router de memoria. Una ruta es pública salvo que lleve
 * `meta.requiereSesion`; la guardia (router/guardia.ts) es quien lo hace cumplir.
 *
 * Las páginas legales (/privacidad, /terminos, /aviso-legal, /borrar-cuenta) son
 * PÚBLICAS a propósito: la ley y Google Play exigen que se lean sin cuenta, y
 * /borrar-cuenta tiene que funcionar aunque el usuario ya haya desinstalado la app.
 */
export const rutas: RouteRecordRaw[] = [
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
    // "Revisa tu correo" tras el registro (verificación de email): pública,
    // pero solo tiene contenido útil con el email que el registro acaba de
    // guardar en el store (memoria de esta pestaña, no persistido).
    path: '/registro/revisa-correo',
    name: 'registro-revisa-correo',
    component: () => import('../views/RegistroConfirmacionView.vue'),
  },
  {
    // Enlace del correo de verificación (?token=...): pública a propósito,
    // quien lo abre puede no tener sesión en este navegador.
    path: '/verifica-email',
    name: 'verifica-email',
    component: () => import('../views/VerificaEmailView.vue'),
  },
  {
    path: '/privacidad',
    name: 'privacidad',
    component: () => import('../views/PrivacidadView.vue'),
  },
  {
    path: '/terminos',
    name: 'terminos',
    component: () => import('../views/TerminosView.vue'),
  },
  {
    path: '/aviso-legal',
    name: 'aviso-legal',
    component: () => import('../views/AvisoLegalView.vue'),
  },
  {
    // La exige Google Play: una URL pública para pedir el borrado aunque ya no
    // se tenga la app instalada. Reautentica y llama al DELETE /cuenta.
    path: '/borrar-cuenta',
    name: 'borrar-cuenta',
    component: () => import('../views/BorrarCuentaView.vue'),
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
]
