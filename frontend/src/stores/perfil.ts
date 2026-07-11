import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '../services/api'
import {
  getConvenioParaTrabajador,
  getOcupacion,
  getProvincias,
  getPuestos,
  postSalarioBase,
  type ConvenioResumen,
  type OcupacionResuelta,
  type Puesto,
  type SalarioBase,
} from '../services/convenios'
import { hoyIso, mensajeDeError } from '../lib/formato'

/**
 * Estado del flujo "¿dónde trabajas? ¿de qué trabajas? → tu salario mínimo".
 * Cada paso resetea todo lo que viene después: cambiar de provincia invalida
 * convenio, puesto y salario (nunca mezclamos datos de dos convenios).
 */
export const usePerfilStore = defineStore('perfil', () => {
  const provincias = ref<string[]>([])
  const provincia = ref<string | null>(null)
  const subsector = ref<string | null>(null)
  const convenio = ref<ConvenioResumen | null>(null)
  const puestos = ref<Puesto[]>([])
  const puestoId = ref<string | null>(null)
  const ocupacion = ref<OcupacionResuelta | null>(null)
  const puestoNoMapeado = ref(false)
  const respuestas = ref<Record<string, string>>({})
  const salario = ref<SalarioBase | null>(null)
  const cargando = ref(false)
  const error = ref<string | null>(null)

  /** Preguntas del convenio que el usuario aún no ha contestado. */
  const pendientesSinResponder = computed(
    () => ocupacion.value?.pendientes.filter((p) => !(p.dimension in respuestas.value)) ?? [],
  )

  /**
   * El puesto elegido, con su etiqueta legible (p. ej. "Camarero/a"). Se busca
   * en la lista cargada porque solo guardamos el id (D219: la frase de
   * clasificación necesita nombrar el puesto real, no un texto genérico).
   */
  const puestoSeleccionado = computed(
    () => puestos.value.find((p) => p.id === puestoId.value) ?? null,
  )

  /**
   * Prellenado de la calculadora de horas extra: si la tabla quedó por debajo
   * del SMI manda el suelo legal (con la tabla superada el valor hora saldría
   * infravalorado). Si un backend viejo marca bajoSmi sin mandar minimoLegal,
   * mejor NO prellenar que prellenar con un número que sabemos corto. Solo
   * vale si el mínimo es mensual.
   */
  const salarioMensualPrefill = computed(() => {
    if (!salario.value || salario.value.unidad !== 'EUR/mes') {
      return null
    }
    if (salario.value.bajoSmi) {
      return salario.value.minimoLegal ?? null
    }
    return salario.value.importe
  })

  /**
   * Guard contra carreras: cada acción async captura un id monótono creciente
   * antes del await y solo escribe estado si sigue siendo la petición más
   * reciente. Una respuesta vieja nunca pisa una selección más nueva.
   */
  let peticionActual = 0
  const nuevaPeticion = () => ++peticionActual
  const sigueVigente = (id: number) => id === peticionActual

  async function cargarProvincias() {
    if (provincias.value.length > 0) {
      return
    }
    const miId = nuevaPeticion()
    try {
      cargando.value = true
      const resultado = await getProvincias()
      if (sigueVigente(miId)) {
        provincias.value = resultado
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

  function resetDesdeConvenio() {
    convenio.value = null
    resetDesdePuesto()
  }

  function resetDesdePuesto() {
    puestoId.value = null
    ocupacion.value = null
    puestoNoMapeado.value = false
    respuestas.value = {}
    salario.value = null
    error.value = null
  }

  async function elegirProvincia(valor: string) {
    provincia.value = valor
    subsector.value = null
    resetDesdeConvenio()
    await buscarConvenio()
  }

  async function elegirSubsector(valor: string) {
    subsector.value = valor
    resetDesdeConvenio()
    await buscarConvenio()
  }

  async function buscarConvenio() {
    if (!provincia.value || !subsector.value) {
      return
    }
    const miId = nuevaPeticion()
    try {
      cargando.value = true
      error.value = null
      const resultado = await getConvenioParaTrabajador(provincia.value, subsector.value)
      if (!sigueVigente(miId)) {
        return
      }
      convenio.value = resultado
      if (puestos.value.length === 0) {
        const lista = await getPuestos()
        if (!sigueVigente(miId)) {
          return
        }
        puestos.value = lista
      }
    } catch (e) {
      if (!sigueVigente(miId)) {
        return
      }
      convenio.value = null
      error.value = mensajeDeError(e)
    } finally {
      if (sigueVigente(miId)) {
        cargando.value = false
      }
    }
  }

  async function elegirPuesto(valor: string) {
    if (!convenio.value) {
      return
    }
    resetDesdePuesto()
    puestoId.value = valor
    // La provincia ya la eligió el usuario (paso previo): se siembra como
    // respuesta desde el principio, igual que el flujo de Cuenta (issue #233),
    // para que el árbol condicional de la colectiva no vuelva a preguntarla.
    // El backend pliega las grafías (NombresProvincia) e ignora la respuesta
    // sobrante en los convenios que no indexan por provincia.
    const prefijadas: Record<string, string> = provincia.value
      ? { provincia: provincia.value }
      : {}
    respuestas.value = prefijadas
    const miId = nuevaPeticion()
    try {
      cargando.value = true
      const resultado = await getOcupacion(convenio.value.id, valor, prefijadas)
      if (!sigueVigente(miId)) {
        return
      }
      ocupacion.value = resultado
      if (pendientesSinResponder.value.length === 0) {
        await calcularSalario()
      }
    } catch (e) {
      if (!sigueVigente(miId)) {
        return
      }
      if (e instanceof ApiError && e.status === 404) {
        // El puesto aún no está mapeado en este convenio → modo manual (próximamente).
        puestoNoMapeado.value = true
      } else {
        error.value = mensajeDeError(e)
      }
    } finally {
      if (sigueVigente(miId)) {
        cargando.value = false
      }
    }
  }

  async function responderPendiente(dimension: string, valor: string) {
    if (!convenio.value || !puestoId.value) {
      return
    }
    // Re-resolvemos con las respuestas acumuladas: en los convenios con mapeo
    // CONDICIONAL (Cataluña por zona, Jaén/Asturias/Málaga por establecimiento…)
    // esto revela la siguiente pregunta encadenada o el nivel que resuelve el
    // árbol. En los directos devuelve lo mismo y no estorba.
    // La respuesta NO se confirma hasta que el re-resolve funciona: si falla (un
    // 500/timeout transitorio), la pregunta debe SEGUIR en pantalla para reintentar,
    // no desaparecer dejando al usuario sin salida.
    const candidatas = { ...respuestas.value, [dimension]: valor }
    const miId = nuevaPeticion()
    try {
      cargando.value = true
      error.value = null
      const resultado = await getOcupacion(convenio.value.id, puestoId.value, candidatas)
      if (!sigueVigente(miId)) {
        return
      }
      respuestas.value = candidatas
      ocupacion.value = resultado
      if (pendientesSinResponder.value.length === 0) {
        await calcularSalario()
      }
    } catch (e) {
      if (!sigueVigente(miId)) {
        return
      }
      error.value = mensajeDeError(e)
    } finally {
      if (sigueVigente(miId)) {
        cargando.value = false
      }
    }
  }

  async function calcularSalario() {
    if (!convenio.value || !ocupacion.value) {
      return
    }
    // `ocupacion.dimensiones` YA está completo tras re-resolver: trae las
    // dimensiones de tabla plegadas (mapeo directo) o el nivel que resolvió el
    // árbol (mapeo condicional). NO se mezclan las respuestas crudas, porque en
    // los condicionales son inputs del árbol (tipo de local, categoría…), no
    // dimensiones de la tabla, y romperían la búsqueda exacta del salario.
    const dimensiones = { ...ocupacion.value.dimensiones }
    const miId = nuevaPeticion()
    try {
      cargando.value = true
      error.value = null
      const resultado = await postSalarioBase(convenio.value.id, dimensiones, hoyIso())
      if (sigueVigente(miId)) {
        salario.value = resultado
      }
    } catch (e) {
      if (!sigueVigente(miId)) {
        return
      }
      salario.value = null
      error.value = mensajeDeError(e)
    } finally {
      if (sigueVigente(miId)) {
        cargando.value = false
      }
    }
  }

  /**
   * Reset al cerrar sesión: este store retiene dónde trabaja el usuario
   * (provincia, puesto) y su salario base calculado. En un dispositivo
   * compartido el siguiente usuario no debe verlos yendo a /perfil (ruta
   * pública). Invalida también las peticiones en vuelo. Lo llama
   * limpiarSesion() del store de auth, igual que cuenta/fichajes/resumen.
   */
  function limpiar() {
    nuevaPeticion()
    provincias.value = []
    provincia.value = null
    subsector.value = null
    convenio.value = null
    puestos.value = []
    puestoId.value = null
    ocupacion.value = null
    puestoNoMapeado.value = false
    respuestas.value = {}
    salario.value = null
    cargando.value = false
    error.value = null
  }

  return {
    provincias,
    provincia,
    subsector,
    convenio,
    puestos,
    puestoId,
    ocupacion,
    puestoNoMapeado,
    respuestas,
    salario,
    cargando,
    error,
    pendientesSinResponder,
    puestoSeleccionado,
    salarioMensualPrefill,
    cargarProvincias,
    elegirProvincia,
    elegirSubsector,
    elegirPuesto,
    responderPendiente,
    limpiar,
  }
})
