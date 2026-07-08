import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

// Guard tests de la configuración nativa Android (Capacitor). El manifest lo
// genera el scaffold de Capacitor y un `npx cap add android` descuidado podría
// regenerarlo con los valores por defecto; estos tests fijan las decisiones de
// seguridad que NO deben revertirse (datos laborales sensibles, RGPD).

const leerManifest = (ruta: string): string =>
  readFileSync(fileURLToPath(new URL(ruta, import.meta.url)), 'utf-8')

const manifestMain = leerManifest('./android/app/src/main/AndroidManifest.xml')
const manifestDebug = leerManifest('./android/app/src/debug/AndroidManifest.xml')

describe('AndroidManifest de main (aplica a debug Y release)', () => {
  it('mantiene el backup del sistema desactivado (allowBackup=false)', () => {
    // Con allowBackup=true, Android sube el directorio de datos de la app
    // (incluido app_webview: cookies, IndexedDB, caché del Service Worker)
    // al backup de Google o lo expone vía `adb backup`.
    expect(manifestMain).toContain('android:allowBackup="false"')
    expect(manifestMain).not.toContain('android:allowBackup="true"')
  })

  it('no permite tráfico HTTP en claro (eso vive solo en el manifest de debug)', () => {
    expect(manifestMain).not.toContain('usesCleartextTraffic')
  })
})

describe('AndroidManifest de debug (solo builds de desarrollo)', () => {
  it('permite HTTP en claro para probar contra el backend de la LAN', () => {
    expect(manifestDebug).toContain('android:usesCleartextTraffic="true"')
  })
})
