import { expect, test } from '@playwright/test'

/**
 * Issue #231: quien trabaja en un comedor (restauración colectiva) nunca podía
 * valorar sus horas extra — la jornada y las pagas van por anexo provincial y
 * el motor no las encontraba (422 permanente en las 49 provincias). Este viaje
 * vigila la cadena entera en el flujo anónimo: provincia + "comedor" + puesto
 * → salario mínimo con citas → calculadora de horas extra con importe.
 */
test('cocinero de comedor en Zaragoza calcula sus horas extra', async ({ page }) => {
  await page.goto('/perfil')

  await page.locator('#provincia').selectOption({ label: 'Zaragoza' })
  await page.getByRole('button', { name: 'Comedor de colegio, hospital, empresa...' }).click()
  // La tarjeta del convenio llega async y REMONTA el select de puesto (mismo
  // matiz que en calculadora-anonima.spec.ts).
  await expect(page.getByRole('heading', { name: 'Tu convenio', exact: true })).toBeVisible()
  await page.locator('#puesto').selectOption({ label: 'Cocinero/a' })

  // Si el convenio encadena preguntas, se responde la primera opción de cada
  // una hasta que sale la cifra del salario (patrón del spec anónimo).
  const cifra = page.getByRole('heading', {
    name: /salario base mínimo|mínimo que te corresponde/i,
  })
  const pendiente = page
    .locator('section.paso', { hasText: 'Una cosa más' })
    .locator('button.opcion')
    .first()
  for (let i = 0; i < 4; i++) {
    if (await cifra.isVisible()) {
      break
    }
    if (await pendiente.isVisible()) {
      await pendiente.click()
    } else {
      await page.waitForTimeout(300)
    }
  }
  await expect(cifra).toBeVisible()

  // La calculadora hereda el mínimo del convenio (EUR/mes) y las dimensiones
  // resueltas (la provincia, imprescindible en la colectiva). 10 h de más:
  await page.locator('#horas-extra').fill('10')
  await page.getByRole('button', { name: 'Calcular' }).click()

  // Antes del arreglo, aquí salía el aviso de datos pendientes (422).
  await expect(page.locator('.importe-grande')).toBeVisible()
  await expect(page.locator('.importe-grande')).toContainText('Te deben al menos')
  await expect(page.locator('.importe-grande')).toContainText('€')
})
