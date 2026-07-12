<script setup lang="ts">
import PanelPlegable from './PanelPlegable.vue'

/**
 * Panel "No he ido" de la libreta (D38): registrar una ausencia con un motivo
 * opcional. El estado (abierto/motivo) lo controla el padre con v-model — el
 * botón que lo abre vive en el PADRE (la fila de excepciones de la libreta) y
 * el reset tras un apunte exitoso vale también para este panel.
 *
 * El formulario vive dentro de PanelPlegable: siempre está en el DOM (por eso
 * se despliega animado) pero queda inert mientras está cerrado.
 *
 * Va dentro de un <form> para que Intro dispare el registro igual que el botón.
 */
const motivo = defineModel<string>('motivo', { default: '' })

// `abierto` es solo lectura: el toggle es del padre y este panel nunca se
// cierra solo, así que una prop plana dice la verdad mejor que un v-model.
defineProps<{ abierto: boolean; fichando: boolean }>()

defineEmits<{ registrar: [] }>()
</script>

<template>
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
      <!-- Disclaimer C5, punto 3 (docs/legal/disclaimers.md): versión SIN
           "cifrado" mientras el motivo no esté cifrado en BD (C4 pendiente). -->
      <p class="campo-ayuda">
        El motivo es opcional. Solo se usa para tu propia reclamación y nunca
        se comparte. Si prefieres, déjalo en blanco.
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
