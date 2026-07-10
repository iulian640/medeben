import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '../services/api'
import {
  getConvenioParaTrabajador,
  getOcupacion,
  getProvincias,
  getPuestos,
  type OcupacionResuelta,
  type Puesto,
} from '../services/convenios'
import {
  getPerfilUsuario,
  putPerfilUsuario,
  type PerfilGuardado,
} from '../services/perfilUsuario'
import { mensajeDeError } from '../lib/formato'

/**
 * Perfil laboral guardado en la cuenta (solo con sesión iniciada). Distinto
 * del store `perfil` (flujo anónimo): aquí el estado es el formulario de
 * edición contra GET/PUT /perfil.
 *
 * El PUT del backend es full-replace: `guardar()` manda SIEMPRE el objeto
 * completo. Las dimensiones no se editan aquí — se conservan las cargadas y
 * solo se descartan si el usuario cambia de puesto (serían de otro puesto).
 */
export const useCuentaStore = defineStore('cuenta', () => {
  const provincias = ref<string[]>([])
  const puestos = ref<Puesto[]>([])

  const provincia = ref<string | null>(null)
  const subsector = ref<string | null>(null)
  const puestoId = ref<string | null>(null)
  const salarioBaseMensual = ref<number | null>(null)
  const plusesAnuales = ref<number | null>(null)
  const convenioId = ref<string | null>(null)

  /** Solo lectura tras cargar: para decidir si las dimensiones siguen valiendo. */
  const dimensionesCargadas = ref<Record<string, string> | null>(null)
  const puestoIdCargado = ref<string | null>(null)
  const provinciaCargada = ref<string | null>(null)
  const subsectorCargado = ref<string | null>(null)

  /*
   * Clasificación del puesto en el convenio: el mismo mecanismo de preguntas
   * encadenadas de la calculadora (PR #185). Sin él, esta pantalla guardaba
   * perfiles con `dimensiones: {}` que parecían completos pero dejaban el
   * resumen mensual en un 422 permanente (el motor no sabe tu grupo/nivel).
   */
  const ocupacion = ref<OcupacionResuelta | null>(null)
  const respuestas = ref<Record<string, string>>({})
  const convenioResuelto = ref<string | null>(null)
  const resolviendo = ref(false)
  const puestoNoMapeado = ref(false)

  const pendientesSinResponder = computed(
    () => ocupacion.value?.pendientes.filter((p) => !(p.dimension in respuestas.value)) ?? [],
  )

  const sinPerfil = ref(false)
  const cargando = ref(false)
  const guardando = ref(false)
  const guardado = ref(false)
  const error = ref<string | null>(null)

  /** Guard anti-carrera, mismo patrón que el store del flujo anónimo. */
  let peticionActual = 0
  const nuevaPeticion = () => ++peticionActual
  const sigueVigente = (id: number) => id === peticionActual

  /** Contador propio para las resoluciones de clasificación: cambiar de
   *  puesto no debe invalidar un guardado en vuelo, ni al revés. */
  let resolucionActual = 0
  const nuevaResolucion = () => ++resolucionActual
  const resolucionVigente = (id: number) => id === resolucionActual

  function aplicarPerfil(p: PerfilGuardado) {
    provincia.value = p.provincia
    subsector.value = p.subsector
    puestoId.value = p.puestoId
    salarioBaseMensual.value = p.salarioBaseMensual
    plusesAnuales.value = p.plusesAnuales
    convenioId.value = p.convenioId
    dimensionesCargadas.value = p.dimensiones
    puestoIdCargado.value = p.puestoId
    provinciaCargada.value = p.provincia
    subsectorCargado.value = p.subsector
  }

  /** Lo cargado del servidor sigue intacto: sus dimensiones aún valen. */
  const clasificacionIntacta = () =>
    puestoId.value !== null &&
    puestoId.value === puestoIdCargado.value &&
    provincia.value === provinciaCargada.value &&
    subsector.value === subsectorCargado.value

  /** Vacía TODOS los campos del formulario: como recién creado, sin datos de nadie. */
  function limpiarFormulario() {
    provincia.value = null
    subsector.value = null
    puestoId.value = null
    salarioBaseMensual.value = null
    plusesAnuales.value = null
    convenioId.value = null
    dimensionesCargadas.value = null
    puestoIdCargado.value = null
    provinciaCargada.value = null
    subsectorCargado.value = null
    limpiarClasificacion()
  }

  function limpiarClasificacion() {
    nuevaResolucion()
    ocupacion.value = null
    respuestas.value = {}
    convenioResuelto.value = null
    resolviendo.value = false
    puestoNoMapeado.value = false
  }

  /**
   * Reset completo al cerrar sesión: el store es un singleton y en un
   * dispositivo compartido (caso real en hostelería) el siguiente usuario no
   * debe ver — ni poder guardar como suyos — los datos salariales del
   * anterior. Invalida también las peticiones en vuelo para que una respuesta
   * tardía no repueble el formulario después del logout.
   */
  function limpiar() {
    nuevaPeticion()
    limpiarFormulario()
    provincias.value = []
    puestos.value = []
    sinPerfil.value = false
    cargando.value = false
    guardando.value = false
    guardado.value = false
    error.value = null
  }

  async function cargar() {
    const miId = nuevaPeticion()
    cargando.value = true
    error.value = null
    guardado.value = false
    try {
      const [perfil, listaProvincias, listaPuestos] = await Promise.all([
        getPerfilUsuario().then(
          (p) => p,
          (e) => {
            // 404 = todavía no hay perfil: no es un error, es un formulario vacío.
            if (e instanceof ApiError && e.status === 404) {
              return null
            }
            throw e
          },
        ),
        getProvincias(),
        getPuestos(),
      ])
      if (!sigueVigente(miId)) {
        return
      }
      provincias.value = listaProvincias
      puestos.value = listaPuestos
      if (perfil === null) {
        // Sin perfil en el servidor: el formulario se vacía del todo. Si
        // venía relleno (p. ej. de la cuenta anterior en un dispositivo
        // compartido), esos datos NO son de este usuario.
        limpiarFormulario()
        sinPerfil.value = true
      } else {
        sinPerfil.value = false
        aplicarPerfil(perfil)
        // Perfil con puesto pero SIN dimensiones (guardado antes de que esta
        // pantalla supiera preguntar): se resuelve ya, para que las preguntas
        // pendientes aparezcan y el resumen deje de estar en 422 eterno.
        if (perfil.puestoId !== null && Object.keys(perfil.dimensiones ?? {}).length === 0) {
          resuelveClasificacion()
        }
      }
    } catch (e) {
      if (sigueVigente(miId)) {
        error.value = mensajeDeError(e)
      }
    } finally {
      if (sigueVigente(miId)) {
        cargando.value = false
      }
    }
  }

  /**
   * Resuelve la clasificación del puesto elegido (y dispara las preguntas
   * encadenadas si el convenio las necesita). La vista la llama al cambiar
   * provincia, tipo de sitio o puesto; y cargar() cuando el perfil guardado
   * tiene puesto pero dimensiones vacías (el caso del 422 eterno).
   */
  async function resuelveClasificacion() {
    limpiarClasificacion()
    if (!provincia.value || !subsector.value || !puestoId.value) {
      return
    }
    const miId = nuevaResolucion()
    resolviendo.value = true
    try {
      const conv = await getConvenioParaTrabajador(provincia.value, subsector.value)
      if (!resolucionVigente(miId)) {
        return
      }
      const resultado = await getOcupacion(conv.id, puestoId.value)
      if (!resolucionVigente(miId)) {
        return
      }
      convenioResuelto.value = conv.id
      ocupacion.value = resultado
    } catch (e) {
      if (!resolucionVigente(miId)) {
        return
      }
      if (e instanceof ApiError && e.status === 404) {
        // Puesto sin mapear en este convenio: se guarda sin clasificación,
        // con las cartas boca arriba (la vista lo cuenta).
        puestoNoMapeado.value = true
      } else {
        error.value = mensajeDeError(e)
      }
    } finally {
      if (resolucionVigente(miId)) {
        resolviendo.value = false
      }
    }
  }

  /** Misma disciplina que la calculadora: la respuesta solo se confirma si el
   *  re-resolve funciona; si falla, la pregunta sigue en pantalla. */
  async function responderPendiente(dimension: string, valor: string) {
    if (!convenioResuelto.value || !puestoId.value) {
      return
    }
    const candidatas = { ...respuestas.value, [dimension]: valor }
    const miId = nuevaResolucion()
    resolviendo.value = true
    error.value = null
    try {
      const resultado = await getOcupacion(convenioResuelto.value, puestoId.value, candidatas)
      if (!resolucionVigente(miId)) {
        return
      }
      respuestas.value = candidatas
      ocupacion.value = resultado
    } catch (e) {
      if (resolucionVigente(miId)) {
        error.value = mensajeDeError(e)
      }
    } finally {
      if (resolucionVigente(miId)) {
        resolviendo.value = false
      }
    }
  }

  async function guardar() {
    if (guardando.value) {
      return
    }
    if (!provincia.value || !subsector.value) {
      error.value = 'Elige al menos tu provincia y el tipo de sitio antes de guardar.'
      return
    }
    if (pendientesSinResponder.value.length > 0) {
      // Guardar un puesto a medias volvería al perfil de dimensiones vacías
      // que deja el resumen en un 422 eterno: mejor pedir la respuesta ya.
      error.value = 'Tu convenio necesita una respuesta más (justo arriba) para clasificar tu puesto.'
      return
    }
    // Mismo guard que en cargar(): si limpiar() corre con el PUT en vuelo
    // (logout con red lenta), la respuesta tardía no debe repoblar el
    // formulario — repondría los datos salariales del usuario que se fue, o
    // pisaría los del siguiente que ya haya cargado su perfil.
    const miId = nuevaPeticion()
    guardando.value = true
    error.value = null
    guardado.value = false
    try {
      // Full-replace: SIEMPRE el objeto completo. Prioridad de dimensiones:
      // 1) las de una clasificación recién resuelta (ocupacion.dimensiones ya
      //    trae las respuestas plegadas: es la ÚNICA fuente del cálculo);
      // 2) las cargadas del servidor, si nada de lo que las define cambió y
      //    NO están vacías (unas vacías son el 422 eterno: mejor null honesto);
      // 3) null (sin puesto, puesto sin mapear, o clasificación caducada).
      const cargadasUtiles =
        dimensionesCargadas.value && Object.keys(dimensionesCargadas.value).length > 0
          ? dimensionesCargadas.value
          : null
      const dimensiones = ocupacion.value
        ? { ...ocupacion.value.dimensiones }
        : clasificacionIntacta() && !puestoNoMapeado.value
          ? cargadasUtiles
          : null
      const resultado = await putPerfilUsuario({
        provincia: provincia.value,
        subsector: subsector.value,
        puestoId: puestoId.value,
        dimensiones,
        salarioBaseMensual: salarioBaseMensual.value,
        plusesAnuales: plusesAnuales.value,
      })
      if (!sigueVigente(miId)) {
        return
      }
      aplicarPerfil(resultado)
      sinPerfil.value = false
      guardado.value = true
    } catch (e) {
      if (sigueVigente(miId)) {
        error.value = mensajeDeError(e)
      }
    } finally {
      if (sigueVigente(miId)) {
        guardando.value = false
      }
    }
  }

  /** La vista lo llama al editar cualquier campo: "Guardado" deja de ser verdad. */
  function marcarEdicion() {
    guardado.value = false
  }

  return {
    provincias,
    puestos,
    provincia,
    subsector,
    puestoId,
    salarioBaseMensual,
    plusesAnuales,
    convenioId,
    ocupacion,
    respuestas,
    resolviendo,
    puestoNoMapeado,
    pendientesSinResponder,
    sinPerfil,
    cargando,
    guardando,
    guardado,
    error,
    cargar,
    guardar,
    resuelveClasificacion,
    responderPendiente,
    marcarEdicion,
    limpiar,
  }
})
