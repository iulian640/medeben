<script setup lang="ts">
import type { TipoApunte } from '../services/fichajes'
import PanelPlegable from './PanelPlegable.vue'

/**
 * Panel "¿A otra hora?" de la libreta (D38): fichar entrada o salida a una hora
 * elegida a mano. El estado (abierto/hora) lo controla el padre con v-model para
 * que su reset tras un apunte exitoso valga también para este panel.
 *
 * El formulario vive dentro de PanelPlegable: siempre está en el DOM (por eso
 * puede desplegarse animado en vez de aparecer de golpe) pero queda inert
 * mientras está cerrado, así que fuera de vista tampoco es alcanzable.
 *
 * Va dentro de un <form>: en el móvil, en marcha, poder pulsar Intro para fichar
 * la entrada (la acción más común) es justo lo que pide el caso de uso.
 */
const abierto = defineModel<boolean>('abierto', { default: false })
const hora = defineModel<string>('hora', { default: '' })

defineProps<{ fichando: boolean }>()

defineEmits<{ fichar: [tipo: TipoApunte] }>()
</script>

<template>
  <button
    type="button"
    class="boton-secundario"
    :aria-expanded="abierto"
    @click="abierto = !abierto"
  >
    ¿A otra hora?
  </button>
  <PanelPlegable :abierto="abierto">
    <!-- Intro (submit) ficha la ENTRADA, el caso más común. -->
    <form
      class="tarjeta"
      @submit.prevent="$emit('fichar', 'ENTRADA')"
    >
      <div class="campo">
        <label for="hora-manual">Hora</label>
        <input
          id="hora-manual"
          v-model="hora"
          type="time"
        >
      </div>
      <div class="panel-botones">
        <button
          type="submit"
          class="boton-secundario"
          :disabled="fichando || hora === ''"
        >
          Entrada a esa hora
        </button>
        <button
          type="button"
          class="boton-secundario"
          :disabled="fichando || hora === ''"
          @click="$emit('fichar', 'SALIDA')"
        >
          Salida a esa hora
        </button>
      </div>
    </form>
  </PanelPlegable>
</template>

<style scoped>
.panel-botones {
  display: flex;
  gap: var(--esp-xs);
  flex-wrap: wrap;
}
</style>
