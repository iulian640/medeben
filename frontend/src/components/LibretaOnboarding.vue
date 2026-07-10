<script setup lang="ts">
import { computed, ref } from 'vue'

/**
 * El onboarding de la libreta (D38, "hay que explicárselo muy bien"): tres
 * pasos cortos con el porqué de la mecánica. El flag de visto lo guarda el
 * padre en localStorage (solo el booleano, ningún dato personal).
 *
 * Sin carrusel: cada paso reemplaza al anterior sin más — la jerarquía tipográfica
 * (progreso discreto, título con peso, cuerpo en prosa) es lo que ordena la
 * lectura, no una coreografía de deslizamiento.
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
    titulo: 'A los 14 días, cada día queda protegido',
    texto:
      'Tienes 14 días para completar o corregir un día con calma. Después queda ' +
      'protegido: lo apuntado ya no se reescribe, así que nadie puede cambiarlo en ' +
      'tu contra. Si necesitas rectificar algo, se anota aparte con su fecha, como ' +
      'en una contabilidad. Esa protección es justo lo que hace que tu libreta valga ' +
      'como prueba si algún día reclamas.',
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
    class="onboarding tarjeta"
    aria-labelledby="onboarding-titulo"
  >
    <p
      class="progreso texto-suave texto-sm"
      aria-hidden="true"
    >
      {{ paso + 1 }} de {{ PASOS.length }}
    </p>
    <h2 id="onboarding-titulo">
      {{ PASOS[paso].titulo }}
    </h2>
    <p>
      {{ PASOS[paso].texto }}
    </p>
    <button
      type="button"
      class="boton boton--ancho"
      @click="avanza"
    >
      {{ esUltimo ? 'Empezar a fichar' : 'Siguiente' }}
    </button>
  </section>
</template>

<style scoped>
h2 {
  font-size: var(--tipo-lg);
}
</style>
