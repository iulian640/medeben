/**
 * Base de notificaciones locales (Capacitor). Solo el envoltorio del plugin,
 * probado y tipado: pedir permiso y programar una notificación. La UX real
 * (recordatorios de fichaje, contador de sellado de D38) llega con la
 * pantalla Hoy; ningún componente debe llamar aún a esto.
 */
import { LocalNotifications } from '@capacitor/local-notifications'

/** Datos mínimos para programar una notificación local. */
export interface NotificacionProgramada {
  /** Identificador (int de 32 bits en Android); reutilizarlo reemplaza la notificación. */
  id: number
  titulo: string
  cuerpo: string
  /** Momento en que debe mostrarse. */
  fecha: Date
}

/**
 * Pide permiso para mostrar notificaciones. Si ya está concedido no vuelve
 * a preguntar (en Android 13+ el diálogo del sistema solo aparece una vez).
 * Devuelve si tenemos permiso; el que llama decide qué hacer si es que no.
 */
export async function solicitarPermisoNotificaciones(): Promise<boolean> {
  const actual = await LocalNotifications.checkPermissions()
  if (actual.display === 'granted') {
    return true
  }
  const trasPedir = await LocalNotifications.requestPermissions()
  return trasPedir.display === 'granted'
}

/**
 * Programa una notificación local para una fecha concreta. No comprueba el
 * permiso: pedirlo es decisión de UX del que llama (sin permiso, Android
 * simplemente no la muestra; no hay error que ocultar).
 */
export async function programarNotificacion(notificacion: NotificacionProgramada): Promise<void> {
  await LocalNotifications.schedule({
    notifications: [
      {
        id: notificacion.id,
        title: notificacion.titulo,
        body: notificacion.cuerpo,
        schedule: { at: notificacion.fecha },
      },
    ],
  })
}

/**
 * Programa un lote de notificaciones de una vez (una llamada al plugin).
 * Reutilizar ids reemplaza las anteriores: reprogramar es idempotente.
 */
export async function programarNotificaciones(
  notificaciones: NotificacionProgramada[],
): Promise<void> {
  if (notificaciones.length === 0) {
    return
  }
  await LocalNotifications.schedule({
    notifications: notificaciones.map((n) => ({
      id: n.id,
      title: n.titulo,
      body: n.cuerpo,
      schedule: { at: n.fecha },
    })),
  })
}

/** Cancela notificaciones programadas por id. Ids inexistentes se ignoran sin error. */
export async function cancelarNotificaciones(ids: number[]): Promise<void> {
  if (ids.length === 0) {
    return
  }
  await LocalNotifications.cancel({ notifications: ids.map((id) => ({ id })) })
}
