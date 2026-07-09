<script setup lang="ts">
import { ref } from 'vue'

/**
 * Panel de rectificación tardía de la libreta (D38): aparece cuando el backend
 * responde 409 (el día ya está sellado). Explica en cristiano qué implica y solo
 * deja reenviar el apunte como rectificación tardía tras marcar la casilla.
 *
 * La confirmación es estado LOCAL: al reenviar con éxito, el padre oculta este
 * panel (deja de haber conflicto) y el componente se desmonta, así que se
 * reinicia solo — no hace falta que el padre lo resetee.
 */
defineProps<{ fichando: boolean }>()

/**
 * El evento lleva el booleano del checkbox: el padre lo re-comprueba antes de
 * mandar la petición con valor probatorio (comprobación redundante a propósito
 * — el disabled del botón no es la única barrera).
 */
const emit = defineEmits<{ confirmar: [confirmado: boolean] }>()

const confirmado = ref(false)
</script>

<template>
  <section
    class="rectificacion"
    aria-labelledby="rectificacion-titulo"
  >
    <h3 id="rectificacion-titulo">
      Este día ya está sellado
    </h3>
    <p>
      Pasados 14 días, cada día de tu libreta se sella: lo apuntado queda
      fijado como prueba y ya no se cambia.
    </p>
    <p>
      Aun así puedes registrarlo como <strong>rectificación tardía</strong>:
      se guarda aparte, con su propia fecha, y lo sellado no se toca. Como
      prueba vale menos que lo fichado al momento, pero es honesto y queda
      en tu libreta.
    </p>
    <label class="confirmar">
      <input
        v-model="confirmado"
        type="checkbox"
      >
      Entiendo que quedará registrado como rectificación tardía, separado
      del día sellado
    </label>
    <button
      type="button"
      class="secundario"
      :disabled="!confirmado || fichando"
      @click="emit('confirmar', confirmado)"
    >
      Registrar la rectificación
    </button>
  </section>
</template>

<style scoped>
.rectificacion {
  border: 1px solid color-mix(in srgb, var(--color-text) 25%, transparent);
  border-radius: 0.75rem;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.rectificacion h3 {
  font-size: 1.05rem;
}

.confirmar {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.secundario {
  font: inherit;
  padding: 0.75rem 1rem;
  border: 1px solid color-mix(in srgb, var(--color-text) 30%, transparent);
  border-radius: 0.6rem;
  background: var(--color-bg);
  color: var(--color-text);
  cursor: pointer;
}

.secundario:disabled {
  opacity: 0.55;
  cursor: default;
}
</style>
