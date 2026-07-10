import { expect, test } from '@playwright/test'

/**
 * La consulta ANÓNIMA (sin cuenta): el salario mínimo de un puesto en una
 * provincia, con sus citas del boletín — la misma verificación manual del QA
 * del rediseño (Zaragoza camarero), ahora vigilada por CI.
 */
test('consulta anónima de salario mínimo con citas', async ({ page }) => {
  await page.goto('/perfil')

  await page.locator('#provincia').selectOption({ label: 'Zaragoza' })
  await page.getByRole('button', { name: 'Bar, restaurante o cafetería' }).click()
  // La tarjeta del convenio llega async y REMONTA el select de puesto:
  // esperar a verla evita elegir el puesto en un nodo que va a morir.
  await expect(page.getByRole('heading', { name: 'Tu convenio', exact: true })).toBeVisible()
  await page.locator('#puesto').selectOption({ label: 'Camarero/a' })

  // Si el convenio encadena preguntas (tipo/categoría del local...), se
  // responde la primera opción de cada una hasta que sale la cifra.
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
  // La cifra en euros y la cita con enlace al boletín oficial.
  await expect(page.getByText(/€/).first()).toBeVisible()
  await expect(
    page.getByRole('link', { name: /boletín oficial/i }).first(),
  ).toBeVisible()
})
