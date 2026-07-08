import type { CapacitorConfig } from '@capacitor/cli'

// Envoltorio nativo (Android) de la PWA. La web normal no pasa por aquí:
// este fichero solo lo lee `npx cap ...` al sincronizar/compilar la app nativa.
const config: CapacitorConfig = {
  appId: 'es.tedeben.app',
  appName: 'TeDeben',
  webDir: 'dist',
}

export default config
