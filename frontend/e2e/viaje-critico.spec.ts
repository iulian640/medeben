import { expect, test } from '@playwright/test'
import { emailUnico, registra } from './util.js'

/**
 * EL viaje de la app, de punta a punta contra el stack real:
 * registro → perfil (con la pregunta encadenada del convenio) → horario
 * semana tipo → fichar entrada y salida → "Lo tuyo" carga SIN la guía del
 * 422 (el bucle "Completar tu perfil" que se rompió en la PR #211).
 */
test('registro → perfil → horario → fichar → resumen', async ({ page }) => {
  await registra(page, emailUnico())

  // --- Perfil en Cuenta: Madrid, hostelería, cocinero ---
  await page.locator('#provincia').selectOption('Madrid')
  await page.getByRole('button', { name: 'Bar, restaurante o cafetería' }).click()
  await page.locator('#puesto').selectOption('cocinero')

  // Pregunta encadenada del convenio (Madrid pide la clase de empresa).
  await page.getByRole('button', { name: 'Clase B' }).click()

  await page.getByRole('button', { name: 'Guardar mi perfil' }).click()
  await expect(page.getByText('Perfil guardado.')).toBeVisible()

  // --- Horario: semana tipo con un turno el lunes ---
  await page.getByRole('link', { name: 'Editar tu horario' }).click()
  await expect(page).toHaveURL(/\/horario$/)
  await page.getByRole('button', { name: 'Añadir turno' }).first().click()
  await page.getByLabel('Lunes, tramo 1, entrada').fill('09:00')
  await page.getByLabel('Lunes, tramo 1, salida').fill('17:00')
  await page.getByRole('button', { name: 'Guardar el horario' }).click()
  await expect(page.getByText('Horario guardado.')).toBeVisible()

  // --- Fichar hoy: entro y salgo ---
  await page.getByRole('link', { name: 'Libreta' }).click()
  // Primera visita: el onboarding de la libreta sellada (D38), paso a paso.
  const empezar = page.getByRole('button', { name: 'Empezar a fichar' })
  const siguiente = page.getByRole('button', { name: 'Siguiente' })
  for (let i = 0; i < 6 && !(await empezar.isVisible()); i++) {
    await siguiente.click()
  }
  await empezar.click()
  await page.getByRole('button', { name: 'Entro ahora' }).click()
  await expect(page.getByText(/en curso/i).first()).toBeVisible()
  await page.getByRole('button', { name: 'Salgo ahora' }).click()
  // La lectura derivada del día: con un solo tramo la fila se llama "Tu jornada".
  await expect(page.getByText('Tu jornada')).toBeVisible()

  // --- Lo tuyo: con perfil + horario + diario, NADA de guía del 422 ---
  await page.getByRole('link', { name: 'Lo tuyo' }).click()
  await expect(page.getByRole('heading', { name: 'Lo tuyo, este mes' })).toBeVisible()
  await expect(page.locator('.tarjeta.guia')).toHaveCount(0)
  await expect(page.locator('[role="alert"]')).toHaveCount(0)
})
