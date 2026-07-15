import { expect, type Page } from '@playwright/test'

/** Cada viaje estrena cuenta: la BD es real y los tests no comparten estado. */
export function emailUnico(): string {
  return `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@medeben.test`
}

export const PASSWORD_E2E = 'Clave-e2e-2026!'

/**
 * Crea una cuenta nueva y espera a estar dentro. El registro (verificación de
 * email, B4) YA NO inicia sesión automáticamente: aterriza en "revisa tu
 * correo", así que aquí se entra explícitamente con las mismas credenciales
 * — las cuentas sin verificar usan la app con normalidad (decisión de
 * producto), la verificación de verdad la prueba verificacion-email.spec.ts.
 * El access token vive SOLO en memoria; desde aquí se navega por la interfaz
 * (barra inferior, enlaces), como haría el usuario. Una recarga YA no pierde
 * la sesión: se restaura en silencio con el refresh persistido (issue #220,
 * ver sesion-persistente.spec.ts).
 */
export async function registra(page: Page, email: string): Promise<void> {
  await page.goto('/registro')
  await page.locator('#email').fill(email)
  await page.locator('#password').fill(PASSWORD_E2E)
  await page.locator('#repite').fill(PASSWORD_E2E)
  await page.getByRole('button', { name: 'Crear cuenta' }).click()
  await expect(page).toHaveURL(/\/registro\/revisa-correo$/)

  await page.getByRole('link', { name: 'Iniciar sesión' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.locator('#email').fill(email)
  await page.locator('#password').fill(PASSWORD_E2E)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page).toHaveURL(/\/cuenta$/)
}
