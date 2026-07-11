import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { setAuthToken } from '../services/api'
import {
  deleteCuenta,
  getMe,
  postLogin,
  postLogout,
  postRefresh,
  postRegistro,
} from '../services/auth'
import { mensajeDeError } from '../lib/formato'
import {
  borrarSesionPersistida,
  guardarSesionPersistida,
  leerSesionPersistida,
} from '../lib/sesionPersistida'
import { useCuentaStore } from './cuenta'
import { useFichajesStore } from './fichajes'
import { useResumenStore } from './resumen'
import { usePerfilStore } from './perfil'

/**
 * Sesión del usuario. CONTRATO DE SEGURIDAD (issue #220):
 * - El access token (JWT) vive SOLO en memoria — nunca localStorage,
 *   sessionStorage ni cookies legibles por JS. Es el credencial que viaja en
 *   cada petición; un XSS no debe encontrarlo persistido.
 * - El refresh (B4) SÍ se persiste, a propósito: es lo que permite que una
 *   recarga o reapertura de la app re-autentique en silencio en vez de echar
 *   al usuario a login (fricción que mata el hábito de fichar a diario). Es
 *   seguro porque rota en cada uso, se puede revocar y el servidor detecta su
 *   reutilización, así que un token robado del storage se corta.
 * - TODAS las salidas de sesión pasan por limpiarSesion(), que purga además lo
 *   persistido: logout, expulsión por 401, borrado de cuenta y refresh
 *   rechazado dejan el storage vacío por el mismo camino de siempre.
 * - Matiz sobre D38/RGPD ("ningún dato personal se persiste en el navegador"):
 *   un refresh revocable no es el diario del usuario; el resto de D38 sigue en
 *   pie — ni el email ni ningún dato salarial tocan el disco.
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const email = ref<string | null>(null)
  const expiraEn = ref<string | null>(null)
  /** Refresh opaco (B4): también SOLO en memoria; rota en cada renovación. */
  const refreshToken = ref<string | null>(null)
  const refreshExpiraEn = ref<string | null>(null)
  const cargando = ref(false)
  const error = ref<string | null>(null)
  /** Mensaje informativo (p. ej. "tu sesión ha caducado") para la pantalla de login. */
  const aviso = ref<string | null>(null)

  const autenticado = computed(() => token.value !== null)

  async function iniciarSesion(emailForm: string, password: string): Promise<boolean> {
    if (cargando.value) {
      return false
    }
    cargando.value = true
    error.value = null
    aviso.value = null
    try {
      const emitido = await postLogin(emailForm, password)
      token.value = emitido.token
      expiraEn.value = emitido.expiraEn
      refreshToken.value = emitido.refreshToken
      refreshExpiraEn.value = emitido.refreshExpiraEn
      email.value = emailForm
      setAuthToken(emitido.token)
      // Persistimos SOLO el refresh (issue #220): sobrevive a la recarga.
      guardarSesionPersistida({
        refreshToken: emitido.refreshToken,
        refreshExpiraEn: emitido.refreshExpiraEn,
      })
      return true
    } catch (e) {
      limpiarSesion()
      error.value = mensajeDeError(e)
      return false
    } finally {
      cargando.value = false
    }
  }

  async function registrarse(emailForm: string, password: string): Promise<boolean> {
    if (cargando.value) {
      return false
    }
    cargando.value = true
    error.value = null
    try {
      await postRegistro(emailForm, password)
    } catch (e) {
      error.value = mensajeDeError(e)
      return false
    } finally {
      cargando.value = false
    }
    // Cuenta creada: entramos directamente con las mismas credenciales.
    return iniciarSesion(emailForm, password)
  }

  /**
   * Punto central de limpieza: por aquí pasan tanto el logout manual
   * (cerrarSesion) como la expulsión por 401 (sesionCaducada). Además del
   * token se vacían los stores de cuenta y de fichajes — son singletons y, en
   * un dispositivo compartido, el siguiente usuario no debe heredar ni los
   * datos salariales ni la libreta del anterior. La dependencia va en un solo
   * sentido (auth → cuenta/fichajes; ninguno importa auth), así que no hay
   * ciclo entre stores.
   */
  function limpiarSesion() {
    token.value = null
    email.value = null
    expiraEn.value = null
    refreshToken.value = null
    refreshExpiraEn.value = null
    setAuthToken(null)
    // Purga el refresh persistido (issue #220): como toda salida de sesión
    // pasa por aquí, logout, 401, borrado de cuenta y refresh rechazado dejan
    // el storage vacío sin tener que acordarse cada uno por su lado.
    borrarSesionPersistida()
    useCuentaStore().limpiar()
    useFichajesStore().limpiar()
    useResumenStore().limpiar()
    usePerfilStore().limpiar()
  }

  /**
   * Logout voluntario: además de limpiar en local, REVOCA el refresh en el
   * servidor (B4, logout real). En dos pasos y sin esperar la red: la sesión
   * local muere ya aunque el POST tarde o falle (mejor un token huérfano que
   * una sesión viva en un dispositivo compartido).
   */
  function cerrarSesion() {
    const enServidor = refreshToken.value
    if (enServidor !== null) {
      void postLogout(enServidor).catch(() => {
        // Sin red no hay revocación remota: el refresh caduca solo.
      })
    }
    limpiarSesion()
    error.value = null
    aviso.value = null
  }

  /** 401 con token: la sesión ya no vale. Se limpia y se avisa en el login. */
  function sesionCaducada() {
    limpiarSesion()
    aviso.value = 'Tu sesión ha caducado. Entra de nuevo, por favor.'
  }

  /**
   * Renueva la sesión con el refresh (B4). La llama el cliente API (via
   * main.ts) cuando un 401 delata el access caducado: si devuelve true, la
   * petición original se reintenta con el token rotado; si false, expulsión.
   * Mismo blindaje de sesión cruzada que borrarCuenta: si la sesión cambió con
   * el refresh en vuelo, el resultado se descarta (y se revoca, para no dejar
   * una sesión huérfana viva en el servidor).
   */
  async function refrescar(): Promise<boolean> {
    const enUso = refreshToken.value
    if (enUso === null) {
      return false
    }
    try {
      const emitido = await postRefresh(enUso)
      if (refreshToken.value !== enUso) {
        void postLogout(emitido.refreshToken).catch(() => {})
        return false
      }
      token.value = emitido.token
      expiraEn.value = emitido.expiraEn
      refreshToken.value = emitido.refreshToken
      refreshExpiraEn.value = emitido.refreshExpiraEn
      setAuthToken(emitido.token)
      // Re-persistimos el refresh ROTADO (issue #220): el anterior ya no vale,
      // y así la siguiente recarga arranca del token vigente.
      guardarSesionPersistida({
        refreshToken: emitido.refreshToken,
        refreshExpiraEn: emitido.refreshExpiraEn,
      })
      return true
    } catch {
      // Refresh caducado, revocado o reutilizado: no hay renovación posible.
      return false
    }
  }

  /**
   * Restauración de sesión al arrancar (issue #220). Lee el refresh persistido
   * y re-autentica en silencio, para que una recarga o reapertura de la app no
   * eche al usuario a la pantalla de login. Contrato:
   * - Si ya hay sesión viva, NO la pisa: devuelve true sin tocar nada (idempotente).
   * - Sin nada persistido, o con el refresh ya caducado (o con caducidad
   *   ilegible): purga el storage y devuelve false SIN ir a la red — un refresh
   *   muerto no merece un round-trip ni un 401 seguro.
   * - Con un refresh vivo: lo carga en memoria y renueva (refrescar, que rota y
   *   re-persiste). Si el servidor lo rechaza (revocado, reutilizado, caducado),
   *   limpiarSesion purga memoria Y storage y devuelve false.
   * - Tras renovar, intenta getMe() para recuperar el email; si getMe falla por
   *   red la sesión SIGUE siendo válida (el email queda null, no se expulsa).
   */
  async function restaurarSesion(): Promise<boolean> {
    if (autenticado.value) {
      return true
    }
    const guardado = leerSesionPersistida()
    if (guardado === null) {
      return false
    }
    const caducidad = Date.parse(guardado.refreshExpiraEn)
    if (Number.isNaN(caducidad) || caducidad <= Date.now()) {
      borrarSesionPersistida()
      return false
    }
    refreshToken.value = guardado.refreshToken
    refreshExpiraEn.value = guardado.refreshExpiraEn
    const renovado = await refrescar()
    if (!renovado) {
      limpiarSesion()
      return false
    }
    try {
      const yo = await getMe()
      email.value = yo.email
    } catch {
      // getMe caído (sin red): la sesión ya es válida; el email se queda null.
    }
    return true
  }

  /**
   * Restauración perezosa y ÚNICA por carga de página: la guardia de rutas la
   * espera antes de decidir. Cachear la promesa evita que dos navegaciones casi
   * simultáneas al arrancar disparen dos refresh (el segundo rotaría el token
   * que el primero está usando y el servidor lo tomaría por reutilización).
   */
  let restauracionEnCurso: Promise<boolean> | null = null
  function asegurarRestauracion(): Promise<boolean> {
    restauracionEnCurso ??= restaurarSesion()
    return restauracionEnCurso
  }

  const borrando = ref(false)
  /** Error del borrado de cuenta (contraseña incorrecta...), para su propio panel. */
  const errorBorrado = ref<string | null>(null)

  /** El panel de borrado limpia su error al abrirse o cancelarse (review HIGH):
   *  un error de un intento anterior no puede reaparecer en un intento nuevo. */
  function limpiarErrorBorrado() {
    errorBorrado.value = null
  }

  /**
   * Borrado de cuenta (RGPD art. 17). Si el servidor confirma, la sesión se
   * limpia ENTERA (misma rutina que el logout: en un dispositivo compartido no
   * queda nada del usuario borrado) y se deja un aviso de despedida. Si falla
   * (contraseña incorrecta → 403, que a propósito no expulsa), la sesión sigue
   * viva y el error se enseña donde se pidió el borrado.
   */
  async function borrarCuenta(password: string): Promise<boolean> {
    if (borrando.value) {
      return false
    }
    borrando.value = true
    errorBorrado.value = null
    // Capturado ANTES del await (review CRITICAL, mismo patrón que api.ts):
    // si la sesión cambia con el DELETE en vuelo (dispositivo compartido: el
    // dueño sale y entra otra persona), la resolución tardía no puede limpiar
    // la sesión NUEVA ni dejarle el aviso de despedida de la cuenta borrada.
    const tokenAlEmpezar = token.value
    try {
      await deleteCuenta(password)
    } catch (e) {
      errorBorrado.value = mensajeDeError(e)
      return false
    } finally {
      borrando.value = false
    }
    if (token.value === tokenAlEmpezar) {
      limpiarSesion()
      aviso.value = 'Tu cuenta y todos tus datos se han borrado.'
    }
    return true
  }

  return {
    // Solo lectura hacia fuera: nadie puede tocar el token sin pasar por las
    // acciones del store (que mantienen el cliente API sincronizado). El JWT
    // que viaja lo gestiona services/api.ts.
    token: computed(() => token.value),
    refreshToken: computed(() => refreshToken.value),
    refreshExpiraEn: computed(() => refreshExpiraEn.value),
    email,
    expiraEn,
    cargando,
    error,
    aviso,
    autenticado,
    borrando,
    errorBorrado,
    iniciarSesion,
    registrarse,
    cerrarSesion,
    sesionCaducada,
    refrescar,
    restaurarSesion,
    asegurarRestauracion,
    borrarCuenta,
    limpiarErrorBorrado,
  }
})
