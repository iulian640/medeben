import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '../services/api'
import {
  getEstadoDia,
  postApunte,
  type ApunteGuardado,
  type ApuntePeticion,
  type EstadoDiaGuardado,
} from '../services/fichajes'
import { getHorarioSemana, type HorarioEfectivo } from '../services/horario'
import { mensajeDeError } from '../lib/formato'
import { sumarDias } from '../lib/libreta'

const DIAS_SEMANA = 7

/**
 * Un día de la vista de semana: su estado, o null si ESA petición falló.
 * Un fallo puntual en un día no tumba los otros seis (resiliencia por día).
 */
export interface DiaDeSemana {
  fecha: string
  estado: EstadoDiaGuardado | null
}

/**
 * La libreta sellada (D38) en el cliente: el día de hoy con sus apuntes y la
 * vista de semana. Aquí no se deriva ningún estado — tras cada apunte se
 * relee el día del backend, que es quien deriva todo del diario.
 *
 * Este store guarda datos laborales del usuario: se limpia SIEMPRE desde
 * limpiarSesion() del store de auth (mismo punto central que cuenta).
 */
export const useFichajesStore = defineStore('fichajes', () => {
  // --- Pantalla "Hoy" ---
  const dia = ref<EstadoDiaGuardado | null>(null)
  const cargando = ref(false)
  const fichando = ref(false)
  const error = ref<string | null>(null)
  /** El último apunte que entró bien: para el microcopy "✓ sellado a las HH:MM". */
  const ultimoSello = ref<ApunteGuardado | null>(null)
  /** El POST devolvió 409: el día ya está sellado y toca explicar la rectificación tardía. */
  const conflictoSellado = ref(false)

  // --- Pantalla "Semana" ---
  const lunes = ref<string | null>(null)
  const semana = ref<DiaDeSemana[]>([])
  const horario = ref<HorarioEfectivo | null>(null)
  const cargandoSemana = ref(false)
  const errorSemana = ref<string | null>(null)

  /** Guard anti-carrera, mismo patrón que el store de cuenta. */
  let peticionActual = 0
  const nuevaPeticion = () => ++peticionActual
  const sigueVigente = (id: number) => id === peticionActual

  async function cargarDia(fecha: string) {
    const miId = nuevaPeticion()
    cargando.value = true
    error.value = null
    conflictoSellado.value = false
    ultimoSello.value = null
    try {
      const estado = await getEstadoDia(fecha)
      if (!sigueVigente(miId)) {
        return
      }
      dia.value = estado
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
   * Manda un apunte y relee el día. Devuelve true si el apunte quedó
   * guardado (aunque la relectura posterior fallara). Un 409 = día sellado:
   * se expone en conflictoSellado para que la UI pida la confirmación de
   * rectificación tardía con fricción.
   */
  async function fichar(peticion: ApuntePeticion): Promise<boolean> {
    if (fichando.value) {
      // Doble submit: ya hay un POST en vuelo, este toque no manda nada.
      return false
    }
    const miId = nuevaPeticion()
    fichando.value = true
    error.value = null
    let apuntado = false
    try {
      const apunte = await postApunte(peticion)
      apuntado = true
      if (sigueVigente(miId)) {
        ultimoSello.value = apunte
        conflictoSellado.value = false
      }
      // El estado del día lo deriva SIEMPRE el backend: se relee, no se calcula aquí.
      const estado = await getEstadoDia(peticion.fecha)
      if (sigueVigente(miId)) {
        dia.value = estado
      }
    } catch (e) {
      if (sigueVigente(miId)) {
        if (apuntado) {
          // El apunte entró bien; solo falló la relectura del día.
          error.value = `Tu apunte se ha guardado, pero no se ha podido recargar el día. ${mensajeDeError(e)}`
        } else {
          conflictoSellado.value = e instanceof ApiError && e.status === 409
          error.value = mensajeDeError(e)
        }
      }
    } finally {
      // El mutex se libera SIEMPRE: es estado de ESTA acción, no de la última
      // vista. Si se condicionara a sigueVigente, navegar mientras el POST
      // está en vuelo (cargarDia adelanta el contador) lo dejaría pegado en
      // true y los botones de fichar quedarían deshabilitados para siempre.
      fichando.value = false
    }
    return apuntado
  }

  async function cargarSemana(lunesIso: string) {
    const miId = nuevaPeticion()
    cargandoSemana.value = true
    errorSemana.value = null
    try {
      const fechas = Array.from({ length: DIAS_SEMANA }, (_, i) => sumarDias(lunesIso, i))
      const [resultados, horarioSemana] = await Promise.all([
        // allSettled: un 500 puntual en UN día no descarta los otros seis.
        Promise.allSettled(fechas.map((fecha) => getEstadoDia(fecha))),
        getHorarioSemana(lunesIso).then(
          (h) => h,
          (e) => {
            // 404 = sin horario configurado: no es un error, solo no hay comparación.
            if (e instanceof ApiError && e.status === 404) {
              return null
            }
            throw e
          },
        ),
      ])
      if (!sigueVigente(miId)) {
        return
      }
      const dias: DiaDeSemana[] = resultados.map((r, i) => ({
        fecha: fechas[i],
        estado: r.status === 'fulfilled' ? r.value : null,
      }))
      if (dias.every((d) => d.estado === null)) {
        // Los 7 fallaron: eso sí es la semana entera caída, no un día suelto.
        const primero = resultados[0]
        errorSemana.value = mensajeDeError(primero.status === 'rejected' ? primero.reason : null)
        return
      }
      lunes.value = lunesIso
      semana.value = dias
      horario.value = horarioSemana
    } catch (e) {
      if (sigueVigente(miId)) {
        errorSemana.value = mensajeDeError(e)
      }
    } finally {
      if (sigueVigente(miId)) {
        cargandoSemana.value = false
      }
    }
  }

  /**
   * Reset completo al cerrar sesión: en un dispositivo compartido el
   * siguiente usuario no debe ver los fichajes del anterior. Invalida también
   * las peticiones en vuelo para que una respuesta tardía no repueble nada.
   */
  function limpiar() {
    nuevaPeticion()
    dia.value = null
    cargando.value = false
    fichando.value = false
    error.value = null
    ultimoSello.value = null
    conflictoSellado.value = false
    lunes.value = null
    semana.value = []
    horario.value = null
    cargandoSemana.value = false
    errorSemana.value = null
  }

  return {
    dia,
    cargando,
    fichando,
    error,
    ultimoSello,
    conflictoSellado,
    lunes,
    semana,
    horario,
    cargandoSemana,
    errorSemana,
    cargarDia,
    fichar,
    cargarSemana,
    limpiar,
  }
})
