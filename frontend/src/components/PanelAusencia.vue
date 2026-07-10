<script setup lang="ts">
import PanelPlegable from './PanelPlegable.vue'

/**
 * Panel "No he ido" de la libreta (D38): registrar una ausencia con un motivo
 * opcional. El estado (abierto/motivo) lo controla el padre con v-model para que
 * su reset tras un apunte exitoso valga también para este panel.
 *
 * El formulario vive dentro de PanelPlegable: siempre está en el DOM (por eso
 * se despliega animado) pero queda inert mientras está cerrado.
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
    class="boton-secundario"
    :aria-expanded="abierto"
    @click="abierto = !abierto"
  >
    No he ido
  </button>
  <PanelPlegable :abierto="abierto">
    <form
      class="tarjeta"
      @submit.prevent="$emit('registrar')"
    >
      <div class="campo">
        <label for="motivo">Motivo (opcional)</label>
        <input
          id="motivo"
          v-model="motivo"
          type="text"
          maxlength="200"
        >
      </div>
      <p class="campo-ayuda">
        El motivo es opcional; si lo escribes, queda en tu libreta.
      </p>
      <button
        type="submit"
        class="boton-secundario"
        :disabled="fichando"
      >
        Registrar ausencia
      </button>
    </form>
  </PanelPlegable>
</template>
