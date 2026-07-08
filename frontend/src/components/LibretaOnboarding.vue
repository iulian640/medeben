<script setup lang="ts">
import { computed, ref } from 'vue'

/**
 * El onboarding de la libreta (D38, "hay que explicárselo muy bien"): tres
 * pasos cortos con el porqué de la mecánica. El flag de visto lo guarda el
 * padre en localStorage (solo el booleano, ningún dato personal).
 */
const emit = defineEmits<{ cerrar: [] }>()

const PASOS = [
  {
    titulo: 'Fichar al momento vale más',
    texto:
      'Cada apunte guarda cuándo lo hiciste. Uno hecho en el momento vale más ' +
      'como prueba que uno reconstruido días después — por eso lo mejor es ' +
      'fichar al entrar y al salir, aunque también puedes apuntarlo más tarde.',
  },
  {
    titulo: 'A los 14 días, cada día se sella',
    texto:
      'Tienes 14 días para completar o corregir cada día. Después se sella: ' +
      'ya no se cambia, y ese sello es justo lo que hace creíble tu libreta ' +
      'si algún día la necesitas para reclamar.',
  },
  {
    titulo: 'Los huecos son normales',
    texto:
      'Si un día no apuntas nada, queda un hueco. No pasa nada: un diario ' +
      'real tiene huecos, y eso le da credibilidad. Un diario perfecto canta.',
  },
]

const paso = ref(0)
const esUltimo = computed(() => paso.value === PASOS.length - 1)

function avanza() {
  if (esUltimo.value) {
    emit('cerrar')
    return
  }
  paso.value = paso.value + 1
}
</script>

<template>
  <section
    class="onboarding"
    aria-labelledby="onboarding-titulo"
  >
    <p
      class="progreso"
      aria-hidden="true"
    >
      {{ paso + 1 }} de {{ PASOS.length }}
    </p>
    <h2 id="onboarding-titulo">
      {{ PASOS[paso].titulo }}
    </h2>
    <p class="texto">
      {{ PASOS[paso].texto }}
    </p>
    <button
      type="button"
      class="principal"
      @click="avanza"
    >
      {{ esUltimo ? 'Empezar a fichar' : 'Siguiente' }}
    </button>
  </section>
</template>

<style scoped>
.onboarding {
  border: 1px solid color-mix(in srgb, var(--color-text) 20%, transparent);
  border-radius: 0.75rem;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.progreso {
  font-size: 0.85rem;
  opacity: 0.7;
}

h2 {
  font-size: 1.2rem;
}

.texto {
  line-height: 1.5;
}

.principal {
  font: inherit;
  font-weight: 600;
  padding: 0.9rem 1.25rem;
  border: none;
  border-radius: 0.75rem;
  background: var(--color-accent);
  color: var(--color-bg);
  cursor: pointer;
  align-self: flex-start;
}
</style>
