import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '../services/api'
import { getResumenMes, type ResumenMensual } from '../services/resumen'
import { hoyIso, mensajeDeError } from '../lib/formato'
import { mesDe, sumarMeses } from '../lib/meses'

/**
 * Estado de la pantalla "me deben X € este mes". Distingue dos "no hay cifra":
 * - `incompleto` (422 del backend): falta un dato del usuario (perfil, horario,
 *   tabla del convenio). No es un fallo — es una guía de qué configurar.
 * - `error`: fallo de verdad (red, 500...), con reintento.
 * Regla de oro heredada del backend: nunca se enseña una cifra inventada.
 */
export const useResumenStore = defineStore('resumen', () => {
  const mes = ref(mesDe(hoyIso()))
  const resumen = ref<ResumenMensual | null>(null)
  const cargando = ref(false)
  const error = ref<string | null>(null)
  const incompleto = ref<string | null>(null)

  const esMesActual = computed(() => mes.value === mesDe(hoyIso()))

  /** Guard anti-carrera, mismo patrón que el resto de stores. */
  let peticionActual = 0
  const nuevaPeticion = () => ++peticionActual
  const sigueVigente = (id: number) => id === peticionActual

  async function cargar(anyoMes: string = mes.value) {
    const miId = nuevaPeticion()
    mes.value = anyoMes
    cargando.value = true
    error.value = null
    incompleto.value = null
    resumen.value = null
    try {
      const resultado = await getResumenMes(anyoMes)
      if (sigueVigente(miId)) {
        resumen.value = resultado
      }
    } catch (e) {
      if (!sigueVigente(miId)) {
        return
      }
      if (e instanceof ApiError && e.status === 422) {
        // Falta un dato configurable: el detail explica QUÉ (RFC 7807).
        incompleto.value = mensajeDeError(e)
      } else {
        error.value = mensajeDeError(e)
      }
    } finally {
      if (sigueVigente(miId)) {
        cargando.value = false
      }
    }
  }

  async function mesAnterior() {
    await cargar(sumarMeses(mes.value, -1))
  }

  /** No pasa del mes en curso: un mes futuro no tiene nada que resumir (400 del backend). */
  async function mesSiguiente() {
    if (esMesActual.value) {
      return
    }
    await cargar(sumarMeses(mes.value, 1))
  }

  /** Reset al cerrar sesión: importes y horas son datos personales. */
  function limpiar() {
    nuevaPeticion()
    mes.value = mesDe(hoyIso())
    resumen.value = null
    cargando.value = false
    error.value = null
    incompleto.value = null
  }

  return {
    mes,
    resumen,
    cargando,
    error,
    incompleto,
    esMesActual,
    cargar,
    mesAnterior,
    mesSiguiente,
    limpiar,
  }
})
