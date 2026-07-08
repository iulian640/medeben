<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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

const fichajes = useFichajesStore()

/** El lunes de la semana en pantalla; se navega de 7 en 7 días. */
const lunes = ref(lunesDe(hoyIso()))

onMounted(() => {
  fichajes.cargarSemana(lunes.value)
})

function cambiaSemana(dias: number) {
  lunes.value = sumarDias(lunes.value, dias)
  fichajes.cargarSemana(lunes.value)
}

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
        class="secundario"
        @click="cambiaSemana(-7)"
      >
        ← Anterior
      </button>
      <p class="titulo-semana">
        Semana del {{ formatearFecha(lunes) }}
      </p>
      <button
        type="button"
        class="secundario"
        @click="cambiaSemana(7)"
      >
        Siguiente →
      </button>
    </nav>

    <p
      v-if="fichajes.cargandoSemana"
      class="cargando"
      role="status"
      aria-live="polite"
    >
      Cargando la semana...
    </p>

    <p
      v-else-if="fichajes.errorSemana"
      class="error"
      role="alert"
    >
      {{ fichajes.errorSemana }}
      <button
        type="button"
        class="secundario"
        @click="fichajes.cargarSemana(lunes)"
      >
        Reintentar
      </button>
    </p>

    <template v-else-if="fichajes.semana.length > 0">
      <ol class="dias">
        <li
          v-for="(d, i) in fichajes.semana"
          :key="d.fecha"
          class="dia"
        >
          <div class="dia-cabecera">
            <h2>
              {{ diaSemanaDe(d.fecha) }}
              <span class="fecha">{{ formatearFecha(d.fecha) }}</span>
            </h2>
            <p class="estado">
              {{ ETIQUETAS_ESTADO[d.estado] }}
            </p>
          </div>
          <p class="minutos">
            Trabajado:
            <template v-if="d.minutosTrabajados !== null">
              {{ formatearMinutos(d.minutosTrabajados) }}
            </template>
            <span
              v-else
              class="sin-calcular"
              title="sin calcular"
              aria-label="sin calcular"
            >—</span>
            <span
              v-if="teoricas[i]"
              class="teoricas"
            >· {{ teoricas[i] }}</span>
          </p>
        </li>
      </ol>

      <p
        v-if="!fichajes.horario"
        class="nota"
      >
        Sin horario configurado: no comparamos con tus horas teóricas.
      </p>
      <p class="nota">
        Los huecos son normales: un diario real tiene huecos, y eso le da credibilidad.
      </p>
    </template>
  </main>
</template>

<style scoped>
.semana {
  max-width: 480px;
  margin: 0 auto;
  padding: 1.25rem 1rem 3rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.cabecera {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}

h1 {
  font-size: 1.5rem;
}

.enlace-hoy {
  color: var(--color-accent);
  font-size: 0.95rem;
  white-space: nowrap;
}

.nav-semana {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.titulo-semana {
  font-weight: 600;
  text-align: center;
}

.secundario {
  font: inherit;
  font-size: 0.9rem;
  padding: 0.5rem 0.75rem;
  border: 1px solid color-mix(in srgb, var(--color-text) 30%, transparent);
  border-radius: 0.6rem;
  background: var(--color-bg);
  color: var(--color-text);
  cursor: pointer;
  white-space: nowrap;
}

.dias {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.dia {
  border: 1px solid color-mix(in srgb, var(--color-text) 20%, transparent);
  border-radius: 0.75rem;
  padding: 0.75rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.dia h2 {
  font-size: 1rem;
}

.fecha {
  font-weight: 400;
  opacity: 0.7;
  font-size: 0.9rem;
}

.estado {
  font-size: 0.95rem;
}

.minutos {
  font-size: 0.9rem;
  opacity: 0.9;
}

.teoricas {
  opacity: 0.75;
}

.nota {
  font-size: 0.85rem;
  opacity: 0.8;
}

.cargando {
  opacity: 0.7;
  font-size: 0.9rem;
}

.error {
  color: #c0392b;
}
</style>
