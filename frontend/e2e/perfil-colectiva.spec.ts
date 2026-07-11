import { expect, test } from '@playwright/test'
import { emailUnico, registra } from './util.js'

/**
 * #233: la colectiva no vuelve a preguntar la provincia que el perfil ya
 * tiene. Los anexos provinciales del convenio estatal van por provincia, pero
 * esa provincia ya la dijo el usuario en el primer desplegable: la
 * clasificación debe salir sola (Zaragoza + comedor + cocinero resuelve su
 * categoría del anexo sin "Una cosa más: ¿provincia?").
 */
test('perfil de colectiva: la provincia del perfil no se vuelve a preguntar', async ({ page }) => {
  await registra(page, emailUnico())

  await page.locator('#provincia').selectOption('Zaragoza')
  await page.getByRole('button', { name: 'Comedor de colegio, hospital, empresa...' }).click()
  await page.locator('#puesto').selectOption('cocinero')

  // La consulta al convenio termina sin dejar ninguna pregunta pendiente.
  await expect(page.getByText('Consultando tu convenio...')).toHaveCount(0)
  await expect(page.getByText(/Una cosa más/)).toHaveCount(0)

  // Y el perfil se guarda completo a la primera (sin el aviso de respuesta pendiente).
  await page.getByRole('button', { name: 'Guardar mi perfil' }).click()
  await expect(page.getByText('Perfil guardado.')).toBeVisible()
})
