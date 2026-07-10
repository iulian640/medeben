<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '../services/api'
import {
  getHorarioSemana,
  getSemanaTipo,
  putSemana,
  putSemanaTipo,
  type DiaHorario,
} from '../services/horario'
import { formatearFecha, mensajeDeError } from '../lib/formato'
import { formatearMinutos, minutosTeoricos } from '../lib/libreta'
import { pulsoExito } from '../lib/animacion'

/**
 * Editor del horario (D38), en dos modos según la ruta:
 * - /horario: la SEMANA TIPO, la que se repite. Cada guardado es una versión
 *   nueva que vale de ahora en adelante; las semanas ya pasadas conservan la
 *   versión que estaba vigente entonces (el pasado no se reescribe).
 * - /horario/semana/:lunes: el horario de UNA semana concreta ("esta semana
 *   me han cambiado el turno"), sin tocar la semana tipo.
 */
const route = useRoute()

const DIAS_SEMANA = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']
const MAX_TRAMOS = 2

const SEMANA_VACIA = (): DiaHorario[] =>
  Array.from({ length: 7 }, () => ({ tramos: [] }))

/** El lunes de la ruta, solo si es de verdad un lunes ISO (2026-07-06). */
function lunesValido(param: unknown): string | null {
  if (typeof param !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(param)) {
    return null
  }
  const fecha = new Date(`${param}T00:00:00Z`)
  if (Number.isNaN(fecha.getTime()) || fecha.toISOString().slice(0, 10) !== param) {
    return null
  }
  return fecha.getUTCDay() === 1 ? param : null
}

const lunes = computed(() => lunesValido(route.params.lunes))
const esEdicionSemana = computed(() => route.params.lunes !== undefined)
const rutaRota = computed(() => esEdicionSemana.value && lunes.value === null)

const dias = ref<DiaHorario[]>(SEMANA_VACIA())
const cargando = ref(true)
const errorCarga = ref<string | null>(null)
/** En modo semana, sin semana tipo previa no hay nada que editar: guía. */
const sinSemanaTipo = ref(false)

async function carga() {
  if (rutaRota.value) {
    cargando.value = false
    return
  }
  cargando.value = true
  errorCarga.value = null
  sinSemanaTipo.value = false
  try {
    if (esEdicionSemana.value) {
      const efectivo = await getHorarioSemana(lunes.value!)
      dias.value = efectivo.dias.map((d) => ({ tramos: [...d.tramos] }))
    } else {
      const tipo = await getSemanaTipo()
      dias.value = tipo.dias.map((d) => ({ tramos: [...d.tramos] }))
    }
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      // Sin horario todavía: la semana tipo arranca en blanco; la edición
      // de una semana concreta necesita antes una semana tipo que editar.
      if (esEdicionSemana.value) {
        sinSemanaTipo.value = true
      } else {
        dias.value = SEMANA_VACIA()
      }
    } else {
      errorCarga.value = mensajeDeError(e)
    }
  } finally {
    cargando.value = false
  }
}

watch(() => route.params.lunes, carga, { immediate: true })

function anadeTramo(indice: number) {
  const tramos = dias.value[indice].tramos
  if (tramos.length < MAX_TRAMOS) {
    tramos.push({ entrada: '', salida: '' })
  }
}

function quitaTramo(indiceDia: number, indiceTramo: number) {
  dias.value[indiceDia].tramos.splice(indiceTramo, 1)
}

function copiaDiaAnterior(indice: number) {
  const anterior = dias.value[indice - 1]
  dias.value[indice] = { tramos: anterior.tramos.map((t) => ({ ...t })) }
}

/** Minutos de un día, solo si todos sus tramos tienen las dos horas. */
function minutosDe(dia: DiaHorario): number | null {
  if (dia.tramos.some((t) => !t.entrada || !t.salida)) {
    return null
  }
  return minutosTeoricos(dia)
}

const minutosSemana = computed(() => {
  let total = 0
  for (const dia of dias.value) {
    const minutos = minutosDe(dia)
    if (minutos === null) {
      return null
    }
    total += minutos
  }
  return total
})

/** No se guarda con tramos a medio rellenar: el backend los rechazaría igual. */
const hayTramosIncompletos = computed(() =>
  dias.value.some((d) => d.tramos.some((t) => !t.entrada || !t.salida)),
)

const guardando = ref(false)
const errorGuardar = ref<string | null>(null)
const guardado = ref(false)
const botonGuardar = ref<HTMLElement | null>(null)

async function guarda() {
  if (guardando.value || hayTramosIncompletos.value) {
    return
  }
  guardando.value = true
  errorGuardar.value = null
  guardado.value = false
  try {
    if (esEdicionSemana.value) {
      await putSemana(lunes.value!, dias.value)
    } else {
      await putSemanaTipo(dias.value)
    }
    guardado.value = true
    if (botonGuardar.value) {
      pulsoExito(botonGuardar.value)
    }
  } catch (e) {
    errorGuardar.value = mensajeDeError(e)
  } finally {
    guardando.value = false
  }
}
</script>

<template>
  <main class="horario">
    <header class="cabecera">
      <h1>{{ esEdicionSemana ? 'El horario de una semana' : 'Tu horario' }}</h1>
      <RouterLink
        class="enlace-volver"
        to="/libreta/semana"
      >
        Tu semana
      </RouterLink>
    </header>

    <p
      v-if="esEdicionSemana && lunes"
      class="texto-suave"
    >
      Solo la semana del <span class="num">{{ formatearFecha(lunes) }}</span>:
      tu semana tipo se queda como está y el cuadrante anterior queda guardado.
    </p>
    <p
      v-else-if="!esEdicionSemana"
      class="texto-suave"
    >
      Tu semana habitual, la que se repite. Vale de ahora en adelante:
      las semanas ya pasadas conservan el horario que tenían.
    </p>

    <div
      v-if="rutaRota"
      class="aviso-bloque"
      role="alert"
    >
      <p>Esa semana no existe: se identifica por su lunes.</p>
      <RouterLink
        class="boton-secundario"
        to="/libreta/semana"
      >
        Volver a tu semana
      </RouterLink>
    </div>

    <p
      v-else-if="cargando"
      class="cargando texto-suave"
      role="status"
      aria-live="polite"
    >
      Cargando tu horario...
    </p>

    <div
      v-else-if="errorCarga"
      class="aviso-bloque aviso-accion"
      role="alert"
    >
      <p>{{ errorCarga }}</p>
      <button
        type="button"
        class="boton-secundario"
        @click="carga"
      >
        Reintentar
      </button>
    </div>

    <section
      v-else-if="sinSemanaTipo"
      class="tarjeta guia"
    >
      <h2 class="titulo-seccion">
        Antes, tu semana tipo
      </h2>
      <p>
        Para ajustar una semana concreta hace falta primero tu horario
        habitual: el que se repite semana a semana.
      </p>
      <RouterLink
        class="boton boton--ancho"
        to="/horario"
      >
        Crear tu horario
      </RouterLink>
    </section>

    <form
      v-else
      class="editor"
      @submit.prevent="guarda"
    >
      <ol class="dias">
        <li
          v-for="(dia, i) in dias"
          :key="DIAS_SEMANA[i]"
          class="dia"
        >
          <div class="dia-cabecera">
            <h2 class="dia-nombre">
              {{ DIAS_SEMANA[i] }}
            </h2>
            <p class="dia-total texto-sm texto-suave">
              <template v-if="minutosDe(dia) === null">
                horas sin completar
              </template>
              <template v-else-if="minutosDe(dia) === 0">
                libre
              </template>
              <template v-else>
                <span class="num">{{ formatearMinutos(minutosDe(dia)!) }}</span>
              </template>
            </p>
          </div>

          <div
            v-for="(tramo, t) in dia.tramos"
            :key="t"
            class="tramo"
          >
            <label class="tramo-campo">
              <span class="texto-sm texto-suave">Entrada</span>
              <input
                v-model="tramo.entrada"
                type="time"
                class="campo"
                required
                :aria-label="`${DIAS_SEMANA[i]}, tramo ${t + 1}, entrada`"
              >
            </label>
            <label class="tramo-campo">
              <span class="texto-sm texto-suave">Salida</span>
              <input
                v-model="tramo.salida"
                type="time"
                class="campo"
                required
                :aria-label="`${DIAS_SEMANA[i]}, tramo ${t + 1}, salida`"
              >
            </label>
            <button
              type="button"
              class="boton-fantasma quitar"
              :aria-label="`Quitar el tramo ${t + 1} del ${DIAS_SEMANA[i].toLowerCase()}`"
              @click="quitaTramo(i, t)"
            >
              Quitar
            </button>
          </div>

          <div class="dia-acciones">
            <button
              v-if="dia.tramos.length < MAX_TRAMOS"
              type="button"
              class="boton-secundario"
              @click="anadeTramo(i)"
            >
              {{ dia.tramos.length === 0 ? 'Añadir turno' : 'Añadir 2º tramo (turno partido)' }}
            </button>
            <button
              v-if="i > 0"
              type="button"
              class="boton-fantasma"
              @click="copiaDiaAnterior(i)"
            >
              Como el {{ DIAS_SEMANA[i - 1].toLowerCase() }}
            </button>
          </div>
        </li>
      </ol>

      <p class="total-semana">
        Total a la semana:
        <strong
          v-if="minutosSemana !== null"
          class="num"
        >{{ minutosSemana === 0 ? '0 h' : formatearMinutos(minutosSemana) }}</strong>
        <span
          v-else
          class="texto-suave"
        >— faltan horas por rellenar</span>
      </p>

      <p class="texto-xs texto-suave">
        Un día sin turnos es un día libre. Si un turno acaba pasada la
        medianoche (20:00 a 02:00), pon la salida tal cual: se entiende que
        cruza al día siguiente.
      </p>

      <button
        ref="botonGuardar"
        type="submit"
        class="boton boton--ancho"
        :disabled="guardando || hayTramosIncompletos"
      >
        {{ guardando ? 'Guardando...' : 'Guardar el horario' }}
      </button>

      <p
        v-if="guardado"
        class="guardado texto-sm"
        role="status"
      >
        Horario guardado.
        <template v-if="!esEdicionSemana">
          Vale desde esta semana en adelante.
        </template>
      </p>
      <p
        v-if="errorGuardar"
        class="aviso-bloque"
        role="alert"
      >
        {{ errorGuardar }}
      </p>
    </form>
  </main>
</template>

<style scoped>
.horario {
  max-width: 30rem;
  margin: 0 auto;
  padding: var(--esp-lg) var(--esp-md) var(--esp-2xl);
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
}

.cabecera {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--esp-md);
}

h1 {
  font-size: var(--tipo-titulo);
}

.enlace-volver {
  font-size: var(--tipo-sm);
  font-weight: var(--peso-etiqueta);
  white-space: nowrap;
}

.cargando {
  padding: var(--esp-xl) 0;
  text-align: center;
}

.aviso-accion {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--esp-sm);
}

.guia {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}

.editor {
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
}

/* Una fila de documento por día, como en "Tu semana": separador, no cajas. */
.dias {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.dia {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
  padding: var(--esp-md) 0;
  border-bottom: 1px solid var(--linea);
}

.dia:last-child {
  border-bottom: none;
}

.dia-cabecera {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--esp-sm);
}

.dia-nombre {
  font-size: var(--tipo-base);
  font-weight: var(--peso-etiqueta);
}

.tramo {
  display: flex;
  align-items: flex-end;
  gap: var(--esp-sm);
}

.tramo-campo {
  display: flex;
  flex-direction: column;
  gap: var(--esp-2xs);
  flex: 1;
}

.quitar {
  white-space: nowrap;
}

.dia-acciones {
  display: flex;
  flex-wrap: wrap;
  gap: var(--esp-sm);
}

.total-semana {
  font-weight: var(--peso-etiqueta);
}

/* Verde SOLO en lo bueno confirmado: el guardado. */
.guardado {
  color: var(--verde);
  font-weight: var(--peso-etiqueta);
}
</style>
