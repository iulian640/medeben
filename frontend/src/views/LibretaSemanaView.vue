<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useFichajesStore } from '../stores/fichajes'
import { formatearFecha, hoyIso } from '../lib/formato'
import {
  ETIQUETAS_ESTADO,
  diaSemanaDe,
  formatearMinutos,
  lunesDe,
  minutosTeoricos,
  sumarDias,
} from '../lib/libreta'
import { revelaEscalonado } from '../lib/animacion'
import type { EstadoDia, EstadoDiaGuardado } from '../services/fichajes'

const fichajes = useFichajesStore()

/** El lunes de la semana en pantalla; se navega de 7 en 7 días. */
const lunes = ref(lunesDe(hoyIso()))
const lista = ref<HTMLElement | null>(null)

onMounted(() => {
  fichajes.cargarSemana(lunes.value)
})

function cambiaSemana(dias: number) {
  lunes.value = sumarDias(lunes.value, dias)
  fichajes.cargarSemana(lunes.value)
}

/** Hay días que fallaron al cargar (resiliencia por día): aviso suave arriba. */
const hayDiasCaidos = computed(() => fichajes.semana.some((d) => d.estado === null))

/**
 * Las horas teóricas de cada día según el horario, ya en texto; null si el
 * usuario no tiene horario configurado (el GET devolvió 404).
 */
const teoricas = computed<(string | null)[]>(() => {
  const horario = fichajes.horario
  if (!horario) {
    return []
  }
  return horario.dias.map((dia) => {
    const minutos = minutosTeoricos(dia)
    return minutos === 0 ? 'libre según tu horario' : `según tu horario: ${formatearMinutos(minutos)}`
  })
})

interface Marcador {
  simbolo: string
  clase: string
}

/**
 * El color por sí solo no basta (daltonismo): cada estado lleva también un
 * símbolo propio. Semántica de la casa: verde SOLO en lo cobrable/completo,
 * tinta suave en lo neutro, alerta solo en lo que de verdad exige atención
 * (un día que ni siquiera se ha podido cargar).
 */
const MARCADORES: Record<EstadoDia, Marcador> = {
  COMPLETO: { simbolo: '✓', clase: 'estado-verde' },
  EN_CURSO: { simbolo: '◐', clase: 'estado-suave' },
  PENDIENTE: { simbolo: '○', clase: 'estado-suave' },
  AUSENCIA: { simbolo: '–', clase: 'estado-suave' },
  HUECO: { simbolo: '·', clase: 'estado-suave' },
}
const MARCADOR_CAIDO: Marcador = { simbolo: '!', clase: 'estado-alerta' }

function marcadorDe(estado: EstadoDia | undefined): Marcador {
  return estado ? MARCADORES[estado] : MARCADOR_CAIDO
}

interface FilaSemana {
  fecha: string
  estado: EstadoDiaGuardado | null
  marcador: Marcador
  etiqueta: string
  teorica: string | null
  esFutura: boolean
}

/** Una fila por día, con todo lo que pinta la plantilla ya resuelto. Un día
 *  futuro no se abre: todavía no hay nada que fichar en él (mismo criterio
 *  que el resumen, que tampoco navega a un mes futuro). */
const filas = computed<FilaSemana[]>(() =>
  fichajes.semana.map((d, i) => ({
    fecha: d.fecha,
    estado: d.estado,
    marcador: marcadorDe(d.estado?.estado),
    etiqueta: d.estado ? ETIQUETAS_ESTADO[d.estado.estado] : 'No se ha podido cargar',
    teorica: teoricas.value[i] ?? null,
    esFutura: d.fecha > hoyIso(), // ISO ordena igual que el calendario
  })),
)

/* Cuando llega la semana, las filas entran escalonadas (un documento recién
 * traído, no una carga de página). Con movimiento reducido no pasa nada de
 * esto: revelaEscalonado deja el estado final puesto de inicio. Si la lista
 * no llegó a pintarse (semana vacía) lista.value es null y no hay nada que
 * animar: mismo guard que usa ResumenMesView para el mismo caso. */
watch(
  () => fichajes.semana,
  async () => {
    await nextTick()
    const dias = lista.value?.querySelectorAll(':scope > *')
    if (dias) {
      revelaEscalonado(dias)
    }
  },
)
</script>

<template>
  <main class="semana">
    <header class="cabecera">
      <h1>Tu semana</h1>
      <RouterLink
        class="enlace-hoy"
        to="/libreta"
      >
        Volver a hoy
      </RouterLink>
    </header>

    <nav
      class="nav-semana"
      aria-label="Cambiar de semana"
    >
      <button
        type="button"
        class="boton-secundario paso-semana"
        @click="cambiaSemana(-7)"
      >
        ← Anterior
      </button>
      <p class="titulo-semana">
        Semana del {{ formatearFecha(lunes) }}
      </p>
      <button
        type="button"
        class="boton-secundario paso-semana"
        @click="cambiaSemana(7)"
      >
        Siguiente →
      </button>
    </nav>

    <p
      v-if="fichajes.cargandoSemana"
      class="cargando texto-suave"
      role="status"
      aria-live="polite"
    >
      Cargando la semana...
    </p>

    <div
      v-else-if="fichajes.errorSemana"
      class="aviso-bloque aviso"
      role="alert"
    >
      <p>{{ fichajes.errorSemana }}</p>
      <button
        type="button"
        class="boton-secundario"
        @click="fichajes.cargarSemana(lunes)"
      >
        Reintentar
      </button>
    </div>

    <template v-else-if="fichajes.semana.length > 0">
      <div
        v-if="hayDiasCaidos"
        class="aviso-bloque aviso"
        role="status"
      >
        <p>Algún día no se ha podido cargar; el resto de la semana sí. Puedes reintentar.</p>
        <button
          type="button"
          class="boton-secundario"
          @click="fichajes.cargarSemana(lunes)"
        >
          Reintentar
        </button>
      </div>

      <ol
        ref="lista"
        class="dias"
      >
        <li
          v-for="fila in filas"
          :key="fila.fecha"
          class="dia"
        >
          <!-- Cada día pasado (u hoy) se abre para completarlo o corregirlo:
               la misma pantalla de fichar, con esa fecha (D38: 14 días de
               margen). Un día futuro es una fila sin enlace: no hay nada que
               fichar en él todavía. -->
          <component
            :is="fila.esFutura ? 'div' : 'RouterLink'"
            class="dia-fila"
            :class="{ 'dia-enlace': !fila.esFutura }"
            v-bind="
              fila.esFutura
                ? {}
                : {
                  to: `/libreta/dia/${fila.fecha}`,
                  'aria-label': `Abrir ${diaSemanaDe(fila.fecha)} ${formatearFecha(fila.fecha)}`,
                }
            "
          >
            <div class="dia-cabecera">
              <h2 class="dia-nombre">
                {{ diaSemanaDe(fila.fecha) }}
                <span class="dia-fecha texto-suave texto-sm num">{{ formatearFecha(fila.fecha) }}</span>
              </h2>
              <p
                class="dia-estado"
                :class="fila.marcador.clase"
              >
                <span
                  class="marcador"
                  aria-hidden="true"
                >{{ fila.marcador.simbolo }}</span>
                {{ fila.etiqueta }}
                <span
                  v-if="!fila.esFutura"
                  class="abrir texto-suave"
                  aria-hidden="true"
                >›</span>
              </p>
            </div>
            <p
              v-if="fila.estado"
              class="dia-minutos texto-sm texto-suave"
            >
              Trabajado:
              <template v-if="fila.estado.minutosTrabajados !== null">
                <span class="num">{{ formatearMinutos(fila.estado.minutosTrabajados) }}</span>
              </template>
              <span
                v-else
                class="sin-calcular"
                title="sin calcular"
                aria-label="sin calcular"
              >—</span>
              <span
                v-if="fila.teorica"
                class="teoricas"
              >· {{ fila.teorica }}</span>
            </p>
          </component>
        </li>
      </ol>

      <div
        v-if="!fichajes.horario"
        class="aviso-bloque aviso"
        role="status"
      >
        <p>Sin horario configurado: no comparamos con tus horas teóricas.</p>
        <RouterLink
          class="boton-secundario"
          to="/horario"
        >
          Crear tu horario
        </RouterLink>
      </div>
      <p
        v-else
        class="texto-sm"
      >
        ¿Te han cambiado el turno?
        <RouterLink :to="`/horario/semana/${lunes}`">
          Editar el horario de esta semana
        </RouterLink>
        ·
        <RouterLink to="/horario">
          tu horario habitual
        </RouterLink>
      </p>
      <p class="texto-sm texto-suave">
        Los huecos son normales: un diario real tiene huecos, y eso le da credibilidad.
      </p>
    </template>
  </main>
</template>

<style scoped>
.semana {
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

.enlace-hoy {
  font-size: var(--tipo-sm);
  font-weight: var(--peso-etiqueta);
  white-space: nowrap;
}

.nav-semana {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--esp-xs);
}

.paso-semana {
  white-space: nowrap;
}

.titulo-semana {
  flex: 1;
  font-weight: var(--peso-etiqueta);
  text-align: center;
}

.cargando {
  padding: var(--esp-xl) 0;
  text-align: center;
}

/* El aviso de la casa, con su botón de acción dentro. */
.aviso {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--esp-sm);
}

/* Una lista, no un panel de tarjetas: cada día es una fila de documento y el
 * separador hace el trabajo de agrupar (nunca una caja por día). */
.dias {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.dia {
  border-bottom: 1px solid var(--linea);
}

.dia:last-child {
  border-bottom: none;
}

/* La fila del día: en pasados/hoy es el enlace que lo abre (color de texto
 * propio, no el verde de los enlaces; fondo suave al pasar). Las futuras
 * comparten layout pero no son enlace. */
.dia-fila {
  display: flex;
  flex-direction: column;
  gap: var(--esp-2xs);
  padding: var(--esp-sm) var(--esp-2xs);
  color: inherit;
  text-decoration: none;
  border-radius: var(--radio-control);
}

.dia-enlace {
  transition: background-color var(--dur-estado) var(--curva-suave);
}

.dia-enlace:hover {
  background: var(--papel-2);
}

.abrir {
  font-weight: var(--peso-titulo);
  margin-left: var(--esp-2xs);
}

.dia-cabecera {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--esp-2xs) var(--esp-sm);
}

.dia-nombre {
  font-size: var(--tipo-base);
  font-weight: var(--peso-etiqueta);
}

.dia-fecha {
  font-weight: var(--peso-texto);
  margin-left: var(--esp-2xs);
}

.dia-estado {
  display: inline-flex;
  align-items: center;
  gap: var(--esp-2xs);
  font-weight: var(--peso-etiqueta);
}

.marcador {
  font-size: var(--tipo-sm);
}

/* Verde SOLO para lo cobrable/completo: un día entero cuenta para lo que te deben. */
.estado-verde {
  color: var(--verde);
}

/* Lo neutro (pendiente, en curso, ausencia, hueco): tinta suave, sin dramatismo. */
.estado-suave {
  color: var(--tinta-suave);
}

/* Alerta SOLO cuando algo exige atención de verdad: un día que ni ha cargado. */
.estado-alerta {
  color: var(--alerta);
}

.dia-minutos {
  margin: 0;
}
</style>
