import type { Router } from 'vue-router'
import type { Pinia } from 'pinia'
import { useAuthStore } from '../stores/auth'

/**
 * Reconciliación de la sesión al DESPERTAR la pestaña (issue #229).
 *
 * Mientras una pestaña duerme (segundo plano, bfcache), otra pudo rotar el
 * refresh compartido, cerrar la sesión o dejar un token quemado. En vez de un
 * listener de storage (los eventos NO llegan a pestañas congeladas — justo el
 * caso que importa en un dispositivo compartido), se relee el ESTADO al
 * despertar, bajo el candado compartido:
 * - `pageshow` con `persisted`: vuelta del bfcache (una carga fresca ya pasa
 *   por la restauración de la guardia de rutas, no necesita esto);
 * - `visibilitychange` a visible: vuelta de segundo plano — cubre también el
 *   resume del WebView de Capacitor, que cambia la visibilidad al volver.
 *
 * Si la reconciliación expulsa (sesión cerrada en otra pestaña, slot pisado
 * por otro login, token quemado), se navega a login con la vuelta preparada,
 * igual que el manejador de 401. Devuelve la función para desregistrar los
 * listeners (los tests la necesitan; la app vive con ellos hasta cerrarse).
 */
export function registrarReconciliacionSesion(router: Router, pinia?: Pinia): () => void {
  const alDespertar = () => {
    void (async () => {
      const auth = useAuthStore(pinia)
      const expulsada = await auth.reconciliar()
      if (!expulsada) {
        return
      }
      const actual = router.currentRoute.value
      if (actual.name === 'login') {
        return
      }
      // Solo se arrastra a login si la ruta EXIGE sesión (review #229): en una
      // ruta pública (home, calculadora anónima) la sesión ya quedó limpia y
      // el aviso espera en login; empujar ahí a alguien que no iba a hacer
      // nada autenticado sería un manotazo.
      if (actual.meta.requiereSesion !== true) {
        return
      }
      void router.push({ name: 'login', query: { redirect: actual.fullPath } })
    })().catch(() => {
      // Reconciliación fallida (p. ej. el navegador denegó el candado): no se
      // propaga como unhandled rejection; el siguiente despertar lo reintenta.
    })
  }
  const alMostrarPagina = (evento: Event) => {
    if ((evento as PageTransitionEvent).persisted) {
      alDespertar()
    }
  }
  const alCambiarVisibilidad = () => {
    if (document.visibilityState === 'visible') {
      alDespertar()
    }
  }
  window.addEventListener('pageshow', alMostrarPagina)
  document.addEventListener('visibilitychange', alCambiarVisibilidad)
  return () => {
    window.removeEventListener('pageshow', alMostrarPagina)
    document.removeEventListener('visibilitychange', alCambiarVisibilidad)
  }
}
