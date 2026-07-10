<script setup lang="ts">
import type { Cita } from '../services/convenios'
import { esUrlSegura } from '../lib/formato'

// Texto SIEMPRE interpolado ({{ }}), nunca v-html: las citas vienen del
// backend pero la regla de seguridad es no interpretar HTML jamás (D34).
// Y solo se enlazan URLs http(s) — esUrlSegura bloquea javascript:, data:...
//
// Key del v-for por índice: una Cita no tiene id y su texto es libre (dos
// citas pueden repetirlo, y las keys duplicadas rompen el diff de Vue). La
// lista se reemplaza entera con cada respuesta, así que el índice es estable.
defineProps<{ citas: Cita[] }>()
</script>

<template>
  <!-- Nota al pie de documento: un separador fino arriba de todo el bloque,
       nunca una franja lateral de color (baneada en este sistema). -->
  <ul
    v-if="citas.length > 0"
    class="citas"
  >
    <li
      v-for="(cita, indice) in citas"
      :key="indice"
      class="cita"
    >
      <p class="cita-texto texto-xs texto-suave">
        {{ cita.texto }}
      </p>
      <a
        v-if="esUrlSegura(cita.url)"
        :href="cita.url"
        target="_blank"
        rel="noopener noreferrer"
        class="cita-enlace texto-xs"
      >Ver boletín oficial</a>
    </li>
  </ul>
</template>

<style scoped>
.citas {
  list-style: none;
  margin: 0;
  padding: var(--esp-sm) 0 0;
  border-top: 1px solid var(--linea);
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}

.cita {
  display: flex;
  flex-direction: column;
  gap: var(--esp-2xs);
}

.cita-enlace {
  font-weight: var(--peso-etiqueta);
  align-self: flex-start;
}
</style>
