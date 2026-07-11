import { beforeEach, vi } from 'vitest'

/**
 * localStorage HERMÉTICO por test (regresión de CI, #229). El entorno real no
 * es estable entre máquinas: con Node ≥25, el getter experimental de Node pisa
 * el localStorage de jsdom y queda undefined (sin --localstorage-file); en CI
 * (Node más viejo) el de jsdom existe DE VERDAD y es compartido por todos los
 * tests del fichero — el blob de sesión que un test persiste se le aparece al
 * siguiente y dispara caminos (revocación de sesión previa) que su mock no
 * cubre. Aquí cada test estrena un Map vacío; los tests que necesiten un
 * storage sembrado, roto o ausente lo stubbean encima, como siempre (su
 * beforeEach corre después de este).
 */
beforeEach(() => {
  const datos = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (clave: string) => datos.get(clave) ?? null,
    setItem: (clave: string, valor: string) => void datos.set(clave, valor),
    removeItem: (clave: string) => void datos.delete(clave),
  })
})
