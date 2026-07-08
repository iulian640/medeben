import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '../services/api'
import { getProvincias, getPuestos, type Puesto } from '../services/convenios'
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

  const sinPerfil = ref(false)
  const cargando = ref(false)
  const guardando = ref(false)
  const guardado = ref(false)
  const error = ref<string | null>(null)

  /** Guard anti-carrera, mismo patrón que el store del flujo anónimo. */
  let peticionActual = 0
  const nuevaPeticion = () => ++peticionActual
  const sigueVigente = (id: number) => id === peticionActual

  function aplicarPerfil(p: PerfilGuardado) {
    provincia.value = p.provincia
    subsector.value = p.subsector
    puestoId.value = p.puestoId
    salarioBaseMensual.value = p.salarioBaseMensual
    plusesAnuales.value = p.plusesAnuales
    convenioId.value = p.convenioId
    dimensionesCargadas.value = p.dimensiones
    puestoIdCargado.value = p.puestoId
  }

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

  async function guardar() {
    if (guardando.value) {
      return
    }
    if (!provincia.value || !subsector.value) {
      error.value = 'Elige al menos tu provincia y el tipo de sitio antes de guardar.'
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
      // Full-replace: SIEMPRE el objeto completo. Las dimensiones del puesto
      // viejo no valen para el nuevo, así que solo se reenvían si el puesto
      // no ha cambiado desde la carga.
      const dimensiones =
        puestoId.value !== null && puestoId.value === puestoIdCargado.value
          ? dimensionesCargadas.value
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
    sinPerfil,
    cargando,
    guardando,
    guardado,
    error,
    cargar,
    guardar,
    marcarEdicion,
    limpiar,
  }
})
