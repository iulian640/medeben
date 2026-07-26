import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, setAuthToken } from '../services/api'
import {
  deleteCuenta,
  getMe,
  postLogin,
  postLogout,
  postReenviaVerificacion,
  postRefresh,
  postRegistro,
  postVerificaEmail,
} from '../services/auth'
import { mensajeDeError } from '../lib/formato'
import { limpiaEstadoLocalUbicacion } from '../services/ubicacion'
import {
  almacenamientoFunciona,
  borrarSesionPersistida,
  borrarSesionPersistidaSi,
  borrarSesionPersistidaSiFamilia,
  generarFamilia,
  guardarSesionPersistida,
  leerSesionPersistida,
} from '../lib/sesionPersistida'
import { conCandadoExclusivo } from '../lib/candadoSesion'
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
 * - Matiz sobre D38/RGPD ("ningún dato personal se persiste en el navegador"):
 *   un refresh revocable no es el diario del usuario; el resto de D38 sigue en
 *   pie — ni el email ni ningún dato salarial tocan el disco.
 *
 * COORDINACIÓN ENTRE PESTAÑAS (issue #229). El refresh persistido es un slot
 * ÚNICO compartido por todas las pestañas, pero cada una guarda su copia en
 * memoria: sin coordinación, la rotación de una pestaña convierte la copia de
 * las demás en un token gastado, y reutilizarlo dispara la alarma antirrobo
 * del servidor (revocaTodas: cae hasta la sesión del móvil donde se ficha).
 * Reglas, todas apoyadas en el candado de lib/candadoSesion.ts:
 * - Toda operación que toque el slot (refrescar, login, logout, reconciliar)
 *   va BAJO el candado y RELEE el slot dentro: la punta persistida es la
 *   verdad; la copia en memoria, solo una caché.
 * - `familia` (UUID aleatorio por login, sin dato personal) marca la cadena de
 *   rotaciones: una pestaña jamás adopta el token de OTRA familia — eso sería
 *   mezclar cuentas en un dispositivo compartido.
 * - Protocolo de token quemado: antes del POST de renovación se persiste el
 *   marcador `enVuelo`. Un marcador huérfano delata un refresh muerto en vuelo
 *   (recarga, cierre, deploy): ese token pudo gastarse, así que NO se reutiliza
 *   — se REVOCA. Revocar un token (gastado o no) lo DESARMA: el backend
 *   comprueba la revocación antes que la reutilización (AuthService.refresca),
 *   así que un token revocado da un 401 plano, nunca revocaTodas. El coste es
 *   un re-login puntual; la alternativa era perder todas las sesiones.
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const email = ref<string | null>(null)
  const expiraEn = ref<string | null>(null)
  /** Refresh opaco (B4): también SOLO en memoria; rota en cada renovación. */
  const refreshToken = ref<string | null>(null)
  const refreshExpiraEn = ref<string | null>(null)
  /** Cadena de rotaciones a la que pertenece esta sesión (issue #229). */
  const familia = ref<string | null>(null)
  const cargando = ref(false)
  const error = ref<string | null>(null)
  /** Mensaje informativo (p. ej. "tu sesión ha caducado") para la pantalla de login. */
  const aviso = ref<string | null>(null)
  /**
   * Email del último registro completado en ESTA pestaña (verificación de
   * email, B4): el registro ya NO inicia sesión, así que la pantalla "revisa
   * tu correo" necesita algo de donde leer la dirección para el prefill y el
   * reenvío. Solo memoria, como el resto de datos personales (D38).
   */
  const emailRecienRegistrado = ref<string | null>(null)
  /**
   * Estado de verificación de la sesión actual, FRESCO de /me — null mientras
   * no se sepa (recién logueado, sin red...). El aviso "confirma tu correo"
   * solo se enseña cuando vale explícitamente false; nunca se cachea entre
   * usuarios (limpiarMemoria lo resetea, dispositivos compartidos).
   */
  const emailVerificado = ref<boolean | null>(null)

  const autenticado = computed(() => token.value !== null)

  /** Revocación fire-and-forget: desarma un token sin bloquear a nadie. */
  function revocaSinEsperar(refresh: string) {
    void postLogout(refresh).catch(() => {
      // Sin red no hay revocación remota: el refresh caduca solo (≤7 días).
    })
  }

  /**
   * Cola local + candado compartido. La cola serializa las secciones DENTRO
   * de esta pestaña: donde no hay Web Locks (dev por http, jsdom) el candado
   * es un no-op y, sin la cola, un reconciliar disparado por visibilitychange
   * podría leer el marcador enVuelo del refresh en vuelo de su PROPIA pestaña
   * y quemarlo como huérfano (review #229). Con locks reales la cola es
   * redundante pero inocua. Sigue sin ser reentrante: nada de llamarla anidada.
   */
  let colaLocal: Promise<unknown> = Promise.resolve()
  function enSeccionSesion<T>(fn: () => Promise<T> | T): Promise<T> {
    const turno = colaLocal.then(() => conCandadoExclusivo(fn))
    colaLocal = turno.catch(() => {
      // Un turno fallido no puede atascar la cola de los siguientes.
    })
    return turno
  }

  async function iniciarSesion(emailForm: string, password: string): Promise<boolean> {
    if (cargando.value) {
      return false
    }
    cargando.value = true
    error.value = null
    aviso.value = null
    // Dispositivo compartido: un login nuevo no puede arrastrar rastros del
    // usuario anterior. El estado de verificación se relee de /me tras entrar
    // (nunca se decide con un valor viejo), y el email de registro deja de
    // hacer falta en cuanto alguien inicia sesión de verdad.
    emailVerificado.value = null
    emailRecienRegistrado.value = null
    try {
      const emitido = await postLogin(emailForm, password)
      await enSeccionSesion(() => {
        // Si el slot compartido guardaba otra sesión (de esta u otra cuenta),
        // su punta se revoca: nadie la va a rotar ya y, sin revocarla, quedaría
        // viva en el servidor hasta 7 días sin que el usuario pudiera cerrarla.
        const previa = leerSesionPersistida()
        if (previa !== null) {
          revocaSinEsperar(previa.refreshToken)
        }
        token.value = emitido.token
        expiraEn.value = emitido.expiraEn
        refreshToken.value = emitido.refreshToken
        refreshExpiraEn.value = emitido.refreshExpiraEn
        familia.value = generarFamilia()
        email.value = emailForm
        setAuthToken(emitido.token)
        // Persistimos SOLO el refresh (issue #220): sobrevive a la recarga.
        const persistido = guardarSesionPersistida({
          refreshToken: emitido.refreshToken,
          refreshExpiraEn: emitido.refreshExpiraEn,
          familia: familia.value,
        })
        if (!persistido) {
          // Escritura fallida (cuota): si quedara el blob de la sesión previa
          // (ya revocada), el siguiente refresh lo tomaría por un slot ajeno y
          // se auto-expulsaría (review #229). Slot limpio y sesión solo-memoria.
          borrarSesionPersistida()
        }
      })
      return true
    } catch (e) {
      // Un login fallido limpia SOLO la memoria: el slot compartido puede ser
      // la sesión viva de otra pestaña y no debe pagar este error.
      limpiarMemoria()
      error.value = mensajeDeError(e)
      return false
    } finally {
      cargando.value = false
    }
  }

  /**
   * Registro (verificación de email, B4): el 201 de /auth/registro es
   * UNIFORME (siempre, exista ya la cuenta o no — la señal real viaja por
   * correo), así que ya NO tiene sentido encadenar el login con la respuesta:
   * no sabemos si la cuenta es nueva de verdad. Se guarda el email para el
   * prefill de "revisa tu correo" y punto; entrar es un paso aparte y
   * explícito (las cuentas sin verificar entran con normalidad).
   */
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
    emailRecienRegistrado.value = emailForm
    return true
  }

  /**
   * Limpieza de la MEMORIA de esta pestaña (el storage compartido lo gestiona
   * cada salida según su caso: candado, compare-and-delete o nada). Además del
   * token se vacían los stores de cuenta y de fichajes — son singletons y, en
   * un dispositivo compartido, el siguiente usuario no debe heredar ni los
   * datos salariales ni la libreta del anterior. La dependencia va en un solo
   * sentido (auth → cuenta/fichajes; ninguno importa auth), así que no hay
   * ciclo entre stores.
   *
   * También purga el estado LOCAL (no de Pinia) de "Anotar dónde fichas"
   * (review HIGH): esas tres claves de localStorage son del dispositivo, no
   * de la cuenta — sin esto, el siguiente usuario en la misma tablet heredaría
   * el flag de activación de quien nunca consintió, y su primer fichaje
   * capturaría y enviaría su posición bajo ese flag ajeno.
   */
  function limpiarMemoria() {
    token.value = null
    email.value = null
    expiraEn.value = null
    refreshToken.value = null
    refreshExpiraEn.value = null
    familia.value = null
    // Dispositivo compartido: el estado de verificación y el email de registro
    // son del USUARIO que se va, no de la pestaña. Sin este reset, el siguiente
    // que entre heredaría el aviso "confirma tu correo" de otra cuenta, o vería
    // en /registro/revisa-correo (ruta pública, botón atrás) un email ajeno con
    // un botón "reenviar" apuntando a esa dirección.
    emailVerificado.value = null
    emailRecienRegistrado.value = null
    setAuthToken(null)
    useCuentaStore().limpiar()
    useFichajesStore().limpiar()
    useResumenStore().limpiar()
    usePerfilStore().limpiar()
    limpiaEstadoLocalUbicacion()
  }

  /**
   * Logout voluntario. Todo lo IRREVERSIBLE ocurre en la parte síncrona (la
   * pestaña puede morir justo después del click — review #229, MEDIUM: si la
   * purga esperase al candado, un dispositivo compartido conservaría un
   * refresh vivo y sin revocar): memoria limpia, punta revocada y slot vacío
   * antes del primer await. La sección con candado de después es la repesca:
   * si otra pestaña estaba rotando durante la purga síncrona, su
   * re-persistencia habrá resucitado el slot y se revoca y purga de nuevo.
   * Las capturas van en locals ANTES de limpiar: el closure no puede fiarse
   * de una memoria que acaba de ponerse a null.
   */
  async function cerrarSesion(): Promise<void> {
    const capturaRefresh = refreshToken.value
    const capturaFamilia = familia.value
    limpiarMemoria()
    error.value = null
    aviso.value = null
    if (capturaRefresh === null) {
      return
    }
    const ahora = leerSesionPersistida()
    const esNuestra = ahora !== null && ahora.familia === capturaFamilia
    // La punta viva es la persistida (rotaciones de otras pestañas incluidas):
    // revocarla cierra la sesión DE VERDAD en el servidor. Con el slot vacío o
    // ajeno, se desarma nuestra copia sin tocar lo de otra cadena.
    revocaSinEsperar(esNuestra ? ahora.refreshToken : capturaRefresh)
    if (ahora === null || esNuestra) {
      borrarSesionPersistida()
    }
    await enSeccionSesion(() => {
      const resucitada = leerSesionPersistida()
      if (resucitada !== null && resucitada.familia === capturaFamilia) {
        revocaSinEsperar(resucitada.refreshToken)
        borrarSesionPersistida()
      }
    })
  }

  /** 401 con token: la sesión ya no vale. Se limpia y se avisa en el login.
   *  El refresh en memoria (el que acaba de ser rechazado en segundo plano) se
   *  captura ANTES de limpiar, para que el purgado del storage sea condicional
   *  y no borre el token que otra pestaña haya rotado (issue #220). El purgado
   *  pasa por la sección de sesión (review #229): un compare-and-delete a pelo
   *  podía colarse entre la lectura y la escritura de otra pestaña. Sin refresh
   *  en memoria NO se toca el slot: esta pestaña no tiene derecho sobre él
   *  (puede ser la sesión superviviente de un 429, o una cadena ajena). */
  function sesionCaducada(): Promise<void> {
    const refrescoRechazado = refreshToken.value
    limpiarMemoria()
    aviso.value = 'Tu sesión ha caducado. Entra de nuevo, por favor.'
    if (refrescoRechazado === null) {
      return Promise.resolve()
    }
    return enSeccionSesion(() => borrarSesionPersistidaSi(refrescoRechazado))
  }

  /**
   * Renueva la sesión con el refresh (B4). La llama el cliente API (via
   * main.ts) cuando un 401 delata el access caducado: si devuelve true, la
   * petición original se reintenta con el token rotado; si false, expulsión.
   *
   * Bajo el candado entre pestañas (issue #229): relee el slot y renueva con
   * la PUNTA persistida (quizá rotada por otra pestaña — usar nuestra copia
   * gastada dispararía revocaTodas), deja el marcador de token quemado durante
   * el POST y lo limpia al re-persistir el rotado. Mismo blindaje de sesión
   * cruzada que borrarCuenta: si la sesión cambió con el refresh en vuelo, el
   * resultado se descarta (y se revoca, para no dejar una sesión huérfana).
   */
  async function refrescar(): Promise<boolean> {
    if (refreshToken.value === null) {
      return false
    }
    return enSeccionSesion(async () => {
      const miRefresh = refreshToken.value
      if (miRefresh === null) {
        // La sesión murió mientras esperábamos el candado.
        return false
      }
      const persistida = leerSesionPersistida()
      let enUso: string
      let cadena: string | null = null
      if (persistida === null) {
        if (almacenamientoFunciona()) {
          // Slot vacío en un storage sano: otra pestaña cerró la sesión a
          // propósito. Renovarla la resucitaría; nuestra copia se desarma por
          // si fuera una punta viva huérfana.
          revocaSinEsperar(miRefresh)
          return false
        }
        // Storage roto (modo privado): sesión solo-memoria, sin nada que
        // coordinar — el comportamiento de antes del issue #220.
        enUso = miRefresh
      } else {
        if (persistida.familia !== familia.value) {
          // Otro login pisó el slot: esta cadena quedó abandonada. Adoptar un
          // token de otra familia mezclaría cuentas — jamás. Desarmar nuestra
          // copia es defensa en profundidad (el login ajeno ya la revocó).
          revocaSinEsperar(miRefresh)
          return false
        }
        if (persistida.enVuelo !== undefined) {
          // Token quemado: un refresh murió en vuelo (pestaña cerrada, deploy)
          // y este token pudo gastarse. Reutilizarlo arriesga revocaTodas;
          // revocarlo lo desarma y cuesta solo un re-login.
          revocaSinEsperar(persistida.refreshToken)
          borrarSesionPersistida()
          return false
        }
        // La punta persistida manda (otra pestaña pudo rotar). Se adopta antes
        // de capturar enUso para que el guard de sesión cruzada siga coherente.
        refreshToken.value = persistida.refreshToken
        refreshExpiraEn.value = persistida.refreshExpiraEn
        enUso = persistida.refreshToken
        cadena = persistida.familia
        if (!guardarSesionPersistida({ ...persistida, enVuelo: enUso })) {
          // Sin marcador escrito no hay red de seguridad si morimos en vuelo,
          // y sin re-persistencia las demás pestañas reutilizarían el token
          // gastado. Mejor forzar un re-login que arriesgar revocaTodas.
          revocaSinEsperar(enUso)
          borrarSesionPersistida()
          return false
        }
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
        if (cadena !== null) {
          // Re-persistir el ROTADO limpia el marcador y conserva la familia:
          // la siguiente pestaña (o recarga) arranca del token vigente.
          const persistido = guardarSesionPersistida({
            refreshToken: emitido.refreshToken,
            refreshExpiraEn: emitido.refreshExpiraEn,
            familia: cadena,
          })
          if (!persistido) {
            // No se pudo publicar la rotación: se retira el blob con marcador
            // para que ninguna pestaña queme enUso (ya gastado) ni lo reuse.
            // Esta pestaña sigue solo-memoria; su siguiente renovación verá el
            // slot vacío y desarmará su punta (review #229: dejar el marcador
            // atrás auto-expulsaba a la propia pestaña sana y dejaba el token
            // rotado vivo sin revocar).
            borrarSesionPersistida()
          }
        }
        return true
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) {
          // Rechazo LIMPIO: el servidor ya decidió que este token está muerto
          // (caducado, revocado o reutilizado). Nada que desarmar; el blob se
          // purga solo si nadie lo ha sustituido entre medias.
          if (cadena !== null) {
            borrarSesionPersistidaSi(enUso)
          }
          return false
        }
        if (e instanceof ApiError && e.status === 429) {
          // Rate limit: el filtro corta ANTES de AuthService, así que seguro
          // que NO rotó y el token sigue vivo (review #229: revocarlo por un
          // throttle transitorio tiraba la sesión del navegador entero). Se
          // desarma el marcador conservando el blob, y esta pestaña suelta su
          // copia para que la expulsión no purgue el slot: pasada la ventana,
          // una recarga (u otra pestaña) restaura la sesión en silencio.
          if (cadena !== null && refreshExpiraEn.value !== null) {
            guardarSesionPersistida({
              refreshToken: enUso,
              refreshExpiraEn: refreshExpiraEn.value,
              familia: cadena,
            })
          }
          refreshToken.value = null
          refreshExpiraEn.value = null
          return false
        }
        // Error de red, timeout o abort: ¿llegó el servidor a rotar? Estado
        // DESCONOCIDO. Dejar el token persistido y armado sería la ruleta del
        // revocaTodas para la siguiente pestaña: se desarma y toca re-login.
        revocaSinEsperar(enUso)
        if (cadena !== null) {
          borrarSesionPersistidaSi(enUso)
        }
        return false
      }
    })
  }

  /**
   * Restauración de sesión al arrancar (issue #220). Lee el refresh persistido
   * y re-autentica en silencio, para que una recarga o reapertura de la app no
   * eche al usuario a la pantalla de login. Contrato:
   * - Si ya hay sesión viva, NO la pisa: devuelve true sin tocar nada (idempotente).
   * - Sin nada persistido, o con el refresh ya caducado (o con caducidad
   *   ilegible): purga el storage y devuelve false SIN ir a la red — un refresh
   *   muerto no merece un round-trip ni un 401 seguro.
   * - Con un refresh vivo: lo carga en memoria (familia incluida) y renueva
   *   (refrescar, que relee bajo el candado, rota y re-persiste). Si el
   *   servidor lo rechaza (revocado, reutilizado, caducado) o el slot delata
   *   un token quemado, se limpia memoria Y storage y devuelve false.
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
      // Compare-and-delete bajo la sección de sesión: si otra pestaña ya
      // escribió algo nuevo, se respeta (issue #220 / review #229).
      await enSeccionSesion(() => borrarSesionPersistidaSi(guardado.refreshToken))
      return false
    }
    refreshToken.value = guardado.refreshToken
    refreshExpiraEn.value = guardado.refreshExpiraEn
    familia.value = guardado.familia
    const renovado = await refrescar()
    if (!renovado) {
      // El slot ya lo dejó como toca refrescar() en TODOS sus caminos de fallo
      // (purga en rechazo limpio y error de red, respeto al blob ajeno, blob
      // conservado a propósito tras un 429): aquí solo se limpia la memoria.
      limpiarMemoria()
      return false
    }
    try {
      const yo = await getMe()
      email.value = yo.email
      emailVerificado.value = yo.emailVerificado
    } catch {
      // getMe caído (sin red): la sesión ya es válida; el email y el estado
      // de verificación se quedan como estaban (probablemente null).
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

  /**
   * Puesta al día al DESPERTAR la pestaña (issue #229): vuelta del bfcache o
   * de segundo plano. Sustituye a un listener de storage a propósito — los
   * eventos no llegan a pestañas congeladas; releer el ESTADO al despertar sí
   * cubre ese caso. Devuelve true si la sesión de esta pestaña ya no vale (el
   * llamante decide la navegación):
   * - slot con la misma familia → se adopta la punta (rotaciones ajenas);
   * - slot vacío con storage sano → otra pestaña cerró la sesión: expulsión;
   * - slot de otra familia → otro login pisó el slot: expulsión (sin tocarlo);
   * - marcador enVuelo huérfano → token quemado: se desarma, purga y expulsión.
   */
  async function reconciliar(): Promise<boolean> {
    if (!autenticado.value) {
      return false
    }
    return enSeccionSesion(() => {
      if (!autenticado.value) {
        return false
      }
      const miRefresh = refreshToken.value
      const persistida = leerSesionPersistida()
      if (persistida === null) {
        if (!almacenamientoFunciona()) {
          // Sesión solo-memoria (modo privado): no hay pestañas que coordinar.
          return false
        }
        if (miRefresh !== null) {
          revocaSinEsperar(miRefresh)
        }
        limpiarMemoria()
        aviso.value = 'Tu sesión se cerró en otra pestaña. Entra de nuevo, por favor.'
        return true
      }
      if (persistida.familia !== familia.value) {
        if (miRefresh !== null) {
          revocaSinEsperar(miRefresh)
        }
        limpiarMemoria()
        aviso.value = 'Tu sesión se cerró en otra pestaña. Entra de nuevo, por favor.'
        return true
      }
      if (persistida.enVuelo !== undefined) {
        // Bajo el candado nadie puede estar refrescando: el marcador es de un
        // refresh que murió en vuelo. Se desarma y toca re-login.
        revocaSinEsperar(persistida.refreshToken)
        borrarSesionPersistida()
        limpiarMemoria()
        aviso.value = 'Tu sesión ha caducado. Entra de nuevo, por favor.'
        return true
      }
      if (persistida.refreshToken !== miRefresh) {
        // Otra pestaña rotó mientras esta dormía: su punta es la buena.
        refreshToken.value = persistida.refreshToken
        refreshExpiraEn.value = persistida.refreshExpiraEn
      }
      return false
    })
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
   * limpia ENTERA (memoria y storage: en un dispositivo compartido no queda
   * nada del usuario borrado) y se deja un aviso de despedida. Si falla
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
      const capturaFamilia = familia.value
      limpiarMemoria()
      aviso.value = 'Tu cuenta y todos tus datos se han borrado.'
      // El servidor ya destruyó las sesiones (cascade); aquí solo se vacía el
      // slot, y solo si sigue siendo de esta cadena (issue #229).
      await enSeccionSesion(() => {
        if (capturaFamilia !== null) {
          borrarSesionPersistidaSiFamilia(capturaFamilia)
        } else {
          borrarSesionPersistida()
        }
      })
    }
    return true
  }

  /**
   * Refresca SOLO el estado de verificación desde /me (verdad fresca de BD).
   * La llama el aviso "confirma tu correo" al aparecer con sesión — nunca hay
   * que fiarse de un valor de ANTES de este login: en una tablet compartida,
   * el aviso del usuario anterior no puede colarse en la sesión del
   * siguiente (por eso limpiarMemoria también lo resetea a null).
   */
  async function actualizarEstadoVerificacion(): Promise<void> {
    if (!autenticado.value) {
      return
    }
    try {
      const yo = await getMe()
      emailVerificado.value = yo.emailVerificado
    } catch {
      // Sin red no hay nada seguro que mostrar: se deja como estaba.
    }
  }

  /**
   * Verificación del enlace del correo (pantalla pública /verifica-email).
   * Se deja que el ApiError se propague: la vista traduce el 400
   * ("enlace no válido o caducado") con mensajeDeError, igual que el resto
   * de formularios de la casa.
   *
   * NO se marca emailVerificado=true de forma optimista: en una tablet
   * compartida la sesión abierta en ESTE navegador puede ser de OTRA cuenta, y
   * el token canjeado no dice de quién es. Se relee el estado real de /me (solo
   * si hay sesión): si el enlace era de la cuenta logueada, /me devuelve true y
   * el aviso desaparece; si es de otra cuenta, su estado no se toca; sin sesión,
   * no hay banner que actualizar.
   */
  async function verificarEmail(token: string): Promise<void> {
    await postVerificaEmail(token)
    await actualizarEstadoVerificacion()
  }

  /**
   * Reenvío del correo de verificación: en pantalla SIEMPRE se confirma el
   * mismo mensaje genérico, acierte o falle la llamada — misma filosofía que
   * /registro (uniforme por diseño, B4). Revelar un resultado distinto
   * delataría si la cuenta existe o ya estaba verificada; el backend ya evita
   * eso con un 202 pase lo que pase, así que aquí tampoco se puede romper esa
   * garantía mostrando un error que sí distinga.
   */
  async function reenviarVerificacion(direccion: string): Promise<void> {
    try {
      await postReenviaVerificacion(direccion)
    } catch {
      // Ver comentario de arriba: el feedback en pantalla es SIEMPRE el mismo.
    }
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
    emailRecienRegistrado,
    emailVerificado,
    iniciarSesion,
    registrarse,
    cerrarSesion,
    sesionCaducada,
    refrescar,
    reconciliar,
    restaurarSesion,
    asegurarRestauracion,
    borrarCuenta,
    limpiarErrorBorrado,
    actualizarEstadoVerificacion,
    verificarEmail,
    reenviarVerificacion,
  }
})
