import { expect, test } from '@playwright/test'
import { emailUnico, registra } from './util.js'

/**
 * Issue #220: recargar la app NO debe echar al usuario a la pantalla de login.
 * Tras registrarse (que aterriza en /cuenta), una recarga re-autentica en
 * silencio con el refresh persistido y deja al usuario justo donde estaba.
 * Antes del arreglo la sesión vivía solo en memoria y el reload redirigía a
 * /login?redirect=/cuenta.
 */
test('recargar mantiene la sesión (no vuelve a login)', async ({ page }) => {
  await registra(page, emailUnico())
  await expect(page).toHaveURL(/\/cuenta$/)

  await page.reload()

  // Sigue dentro: misma ruta y la vista de la cuenta, no la de login.
  await expect(page).toHaveURL(/\/cuenta$/)
  await expect(page.getByRole('heading', { name: 'Tu cuenta' })).toBeVisible()
})
