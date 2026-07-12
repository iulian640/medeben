/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the backend API. Empty in dev (Vite proxy) and same-origin web; set for Capacitor builds. */
  readonly VITE_API_URL?: string
  /**
   * NIF del responsable (LSSI art. 10). Dato sensible: se inyecta SOLO al build
   * de despliegue vía frontend/.env.local (gitignored), nunca se versiona.
   * Ausente en el repo público / CI → el aviso legal muestra un texto de reserva.
   */
  readonly VITE_RESPONSABLE_NIF?: string
  /**
   * Domicilio a efectos de notificaciones del responsable (LSSI art. 10). Dato
   * sensible: mismo trato que VITE_RESPONSABLE_NIF (solo en .env.local, gitignored).
   */
  readonly VITE_RESPONSABLE_DOMICILIO?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
