<script setup lang="ts">
/**
 * Panel "No he ido" de la libreta (D38): registrar una ausencia con un motivo
 * opcional. El estado (abierto/motivo) lo controla el padre con v-model para que
 * su reset tras un apunte exitoso valga también para este panel.
 *
 * Va dentro de un <form> para que Intro dispare el registro igual que el botón.
 */
const abierto = defineModel<boolean>('abierto', { default: false })
const motivo = defineModel<string>('motivo', { default: '' })

defineProps<{ fichando: boolean }>()

defineEmits<{ registrar: [] }>()
</script>

<template>
  <button
    type="button"
    class="secundario"
    :aria-expanded="abierto"
    @click="abierto = !abierto"
  >
    No he ido
  </button>
  <form
    v-if="abierto"
    class="panel"
    @submit.prevent="$emit('registrar')"
  >
    <label for="motivo">Motivo (opcional)</label>
    <input
      id="motivo"
      v-model="motivo"
      type="text"
      maxlength="200"
    >
    <p class="privacidad">
      El motivo es opcional; si lo escribes, queda en tu libreta.
    </p>
    <button
      type="submit"
      class="secundario"
      :disabled="fichando"
    >
      Registrar ausencia
    </button>
  </form>
</template>

<style scoped>
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

.panel {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  border-left: 3px solid color-mix(in srgb, var(--color-text) 20%, transparent);
  padding-left: 0.75rem;
}

.panel label {
  font-weight: 600;
}

.panel input {
  font: inherit;
  padding: 0.65rem 0.75rem;
  border: 1px solid color-mix(in srgb, var(--color-text) 30%, transparent);
  border-radius: 0.6rem;
  background: var(--color-bg);
  color: var(--color-text);
}

.privacidad {
  font-size: 0.85rem;
  opacity: 0.8;
}
</style>
