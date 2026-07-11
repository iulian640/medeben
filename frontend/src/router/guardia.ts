import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'

/**
 * Guardia de navegación (async: vue-router espera la promesa antes de decidir).
 * Antes de nada asegura la restauración de sesión (issue #220): si hay un
 * refresh persistido, re-autentica en silencio tras una recarga. Es perezosa y
 * ÚNICA por carga de página (asegurarRestauracion cachea la promesa), y la
 * ESPERAMOS aquí para las dos ramas — también la de login/registro→cuenta, si
 * no un usuario con sesión restaurable vería un parpadeo de la pantalla de
 * login antes de que la sesión revuelva.
 *
 * - Las rutas con `meta.requiereSesion` piden sesión; sin ella, a login con
 *   `?redirect=` para volver después (validado en destinoTrasLogin).
 * - Login y registro con sesión ya iniciada no tienen sentido: a la cuenta.
 * - Todo lo demás sigue público (el flujo anónimo funciona sin cuenta).
 */
export async function guardiaSesion(
  to: RouteLocationNormalized,
): Promise<boolean | RouteLocationRaw> {
  const auth = useAuthStore()
  await auth.asegurarRestauracion()
  if (to.meta.requiereSesion && !auth.autenticado) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'registro') && auth.autenticado) {
    return { name: 'cuenta' }
  }
  return true
}
