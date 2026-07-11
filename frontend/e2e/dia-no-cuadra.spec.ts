import { expect, test, type Page } from '@playwright/test'
import { emailUnico, registra } from './util.js'

/**
 * Issue #230, contra el stack real: reconstruir un turno partido apuntando
 * los cuatro fichajes en DESORDEN (S 14:00, E 10:00, S 21:00, E 17:00)
 * fundía los tramos en uno de 11h y dejaba el día "En curso" sin señal.
 * Ahora el día queda NO_CUADRA: aviso visible en la libreta, sin jornada
 * inventada, con el diario en bruto intacto, y marcado también en la semana.
 */

/** Apunta una hora por el panel manual; el panel se cierra al apuntar con éxito. */
async function apuntaManual(page: Page, tipo: 'Entrada' | 'Salida', hora: string): Promise<void> {
  const toggle = page.getByRole('button', { name: 'Registrar el turno manualmente' })
  await toggle.click()
  await page.locator('#hora-manual').fill(hora)
  await page.getByRole('button', { name: `${tipo} a esa hora` }).click()
  // El panel se recoge solo cuando el apunte ha entrado: espera determinista.
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
}

test('turno partido reconstruido en desorden → el día dice "No cuadra: revísalo"', async ({ page }) => {
  await registra(page, emailUnico())

  await page.getByRole('link', { name: 'Libreta' }).click()
  // Primera visita: el onboarding de la libreta sellada (D38), paso a paso.
  const empezar = page.getByRole('button', { name: 'Empezar a fichar' })
  const siguiente = page.getByRole('button', { name: 'Siguiente' })
  for (let i = 0; i < 6 && !(await empezar.isVisible()); i++) {
    await siguiente.click()
  }
  await empezar.click()

  // El caso (a) de la issue, tal cual lo reprodujo el QA.
  await apuntaManual(page, 'Salida', '14:00')
  await apuntaManual(page, 'Entrada', '10:00')
  await apuntaManual(page, 'Salida', '21:00')
  await apuntaManual(page, 'Entrada', '17:00')

  // La señal honesta: estado en cristiano + aviso que explica y pide revisión.
  await expect(page.getByText('No cuadra: revísalo')).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('se contradicen')
  // Ni tramo fundido de 11h ni total: no hay lectura que enseñar.
  await expect(page.getByText('10:00 → 21:00')).toHaveCount(0)
  await expect(page.getByText('Llevas apuntado')).toHaveCount(0)
  // La prueba sigue ahí: los 4 apuntes del diario, plegados.
  await expect(page.getByRole('button', { name: 'Ver el diario (4 apuntes)' })).toBeVisible()

  // Y en el repaso de la semana, el día queda marcado — no desaparece.
  await page.getByRole('link', { name: 'Ver la semana' }).click()
  await expect(page.getByText('No cuadra: revísalo')).toBeVisible()
})
