import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, setAuthToken } from '../services/api'
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

  const autenticado = computed(() => token.value !== null)

  /** Revocación fire-and-forget: desarma un token sin bloquear a nadie. */
  function revocaSinEsperar(refresh: string) {
    void postLogout(refresh).catch(() => {
      // Sin red no hay revocación remota: el refresh caduca solo (≤7 días).
    })
  }

  async function iniciarSesion(emailForm: string, password: string): Promise<boolean> {
    if (cargando.value) {
      return false
    }
    cargando.value = true
    error.value = null
    aviso.value = null
    try {
      const emitido = await postLogin(emailForm, password)
      await conCandadoExclusivo(() => {
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
        guardarSesionPersistida({
          refreshToken: emitido.refreshToken,
          refreshExpiraEn: emitido.refreshExpiraEn,
          familia: familia.value,
        })
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
   * Limpieza de la MEMORIA de esta pestaña (el storage compartido lo gestiona
   * cada salida según su caso: candado, compare-and-delete o nada). Además del
   * token se vacían los stores de cuenta y de fichajes — son singletons y, en
   * un dispositivo compartido, el siguiente usuario no debe heredar ni los
   * datos salariales ni la libreta del anterior. La dependencia va en un solo
   * sentido (auth → cuenta/fichajes; ninguno importa auth), así que no hay
   * ciclo entre stores.
   */
  function limpiarMemoria() {
    token.value = null
    email.value = null
    expiraEn.value = null
    refreshToken.value = null
    refreshExpiraEn.value = null
    familia.value = null
    setAuthToken(null)
    useCuentaStore().limpiar()
    useFichajesStore().limpiar()
    useResumenStore().limpiar()
    usePerfilStore().limpiar()
  }

  /**
   * Logout voluntario: la sesión local muere YA (la memoria se limpia antes de
   * tocar la red), y bajo el candado se revoca la punta VIVA de la cadena — la
   * persistida, que puede ser más nueva que nuestra copia si otra pestaña rotó
   * (issue #229: revocar la copia gastada dejaba la rotación viva de zombi).
   * Las capturas van en locals ANTES de limpiar: el closure del candado no
   * puede fiarse de una memoria que acaba de ponerse a null.
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
    await conCandadoExclusivo(() => {
      const persistida = leerSesionPersistida()
      if (persistida !== null && persistida.familia === capturaFamilia) {
        // La punta viva es la persistida (rotaciones de otras pestañas
        // incluidas): revocarla cierra la sesión DE VERDAD en el servidor.
        revocaSinEsperar(persistida.refreshToken)
        borrarSesionPersistida()
      } else {
        // Slot vacío o de otra sesión: solo se desarma nuestra copia, sin
        // tocar lo que otra cadena tenga persistido.
        revocaSinEsperar(capturaRefresh)
      }
    })
  }

  /** 401 con token: la sesión ya no vale. Se limpia y se avisa en el login.
   *  El refresh en memoria (el que acaba de ser rechazado en segundo plano) se
   *  captura ANTES de limpiar, para que el purgado del storage sea condicional
   *  y no borre el token que otra pestaña haya rotado (issue #220). */
  function sesionCaducada() {
    const refrescoRechazado = refreshToken.value
    limpiarMemoria()
    if (refrescoRechazado !== null) {
      borrarSesionPersistidaSi(refrescoRechazado)
    } else {
      borrarSesionPersistida()
    }
    aviso.value = 'Tu sesión ha caducado. Entra de nuevo, por favor.'
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
    return conCandadoExclusivo(async () => {
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
            // El blob con marcador quedó atrás: quien lo lea desarmará enUso
            // (ya gastado, inocuo) y pedirá login. Se desarma ya, por acortar.
            revocaSinEsperar(enUso)
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
      // Compare-and-delete: si otra pestaña ya escribió algo nuevo, se respeta.
      borrarSesionPersistidaSi(guardado.refreshToken)
      return false
    }
    refreshToken.value = guardado.refreshToken
    refreshExpiraEn.value = guardado.refreshExpiraEn
    familia.value = guardado.familia
    const renovado = await refrescar()
    if (!renovado) {
      // Purgado condicional (issue #220): si mientras nuestro refrescar viajaba
      // otra pestaña rotó el token compartido, no borramos su refresh vigente.
      limpiarMemoria()
      borrarSesionPersistidaSi(guardado.refreshToken)
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
    return conCandadoExclusivo(() => {
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
      // El servidor ya destruyó las sesiones (cascade); aquí solo se vacía el
      // slot, y solo si sigue siendo de esta cadena (issue #229).
      if (capturaFamilia !== null) {
        borrarSesionPersistidaSiFamilia(capturaFamilia)
      } else {
        borrarSesionPersistida()
      }
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
    reconciliar,
    restaurarSesion,
    asegurarRestauracion,
    borrarCuenta,
    limpiarErrorBorrado,
  }
})
