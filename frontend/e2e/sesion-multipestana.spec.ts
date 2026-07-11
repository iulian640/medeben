import { expect, test, type Page } from '@playwright/test'
import { emailUnico, registra } from './util.js'

/**
 * Issue #229: dos pestañas comparten el refresh persistido. Al abrir la
 * segunda, esta restaura la sesión y ROTA el token: la copia en memoria de la
 * primera queda gastada. Antes del arreglo, el logout de la primera revocaba
 * esa copia gastada y la rotación viva quedaba de ZOMBI en el servidor —
 * imposible de cerrar para el usuario. Ahora el logout revoca la punta
 * persistida (la viva) bajo el candado entre pestañas.
 */

const CLAVE_SESION = 'medeben.refresh'

async function blobSesion(page: Page): Promise<{ refreshToken: string } | null> {
  const crudo = await page.evaluate((clave) => localStorage.getItem(clave), CLAVE_SESION)
  return crudo === null ? null : (JSON.parse(crudo) as { refreshToken: string })
}

test('cerrar sesión en una pestaña cierra la sesión compartida DE VERDAD (sin zombis)', async ({
  page,
  context,
}) => {
  await registra(page, emailUnico())

  // Segunda pestaña del mismo navegador: restaura en silencio y rota el token.
  const pestanaB = await context.newPage()
  await pestanaB.goto('/cuenta')
  await expect(pestanaB.getByRole('heading', { name: 'Tu cuenta', exact: true })).toBeVisible()

  // La punta VIVA de la cadena tras la rotación de B. La copia que la pestaña
  // A guarda en memoria ya está gastada: el escenario exacto del zombi.
  const punta = await blobSesion(pestanaB)
  expect(punta).not.toBeNull()

  // Logout desde la pestaña A, la de la copia vieja. La revocación remota es
  // fire-and-forget: se espera su respuesta para que el asserto de abajo no
  // corra MÁS que el POST de revocación (flakiness real del primer intento).
  await page.bringToFront()
  const revocacion = page.waitForResponse((r) => r.url().includes('/auth/logout'))
  await page.getByRole('button', { name: 'Cerrar sesión' }).click()
  await revocacion
  await expect(page).toHaveURL(/\/$/)

  // El slot compartido queda vacío (el logout lo purga bajo el candado)...
  await expect.poll(() => blobSesion(page)).toBeNull()

  // ...y la punta viva quedó revocada EN EL SERVIDOR: quien la robara (o el
  // zombi de antes del arreglo) recibe un 401, no una sesión nueva.
  const respuesta = await page.request.post('/api/v1/auth/refresh', {
    data: { refreshToken: punta!.refreshToken },
  })
  expect(respuesta.status()).toBe(401)
})

test('abrir una segunda pestaña no rompe la sesión: ambas siguen dentro tras recargar', async ({
  page,
  context,
}) => {
  await registra(page, emailUnico())

  const pestanaB = await context.newPage()
  await pestanaB.goto('/cuenta')
  await expect(pestanaB.getByRole('heading', { name: 'Tu cuenta', exact: true })).toBeVisible()

  // La primera pestaña recarga DESPUÉS de la rotación de B: debe restaurar
  // desde la punta compartida, no desde su copia vieja.
  await page.reload()
  await expect(page).toHaveURL(/\/cuenta$/)
  await expect(page.getByRole('heading', { name: 'Tu cuenta', exact: true })).toBeVisible()

  // Y la segunda sigue viva también.
  await pestanaB.reload()
  await expect(pestanaB.getByRole('heading', { name: 'Tu cuenta', exact: true })).toBeVisible()
})
