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
  <ul class="citas">
    <li
      v-for="(cita, indice) in citas"
      :key="indice"
      class="cita"
    >
      <span class="cita-texto">{{ cita.texto }}</span>
      <a
        v-if="esUrlSegura(cita.url)"
        :href="cita.url"
        target="_blank"
        rel="noopener noreferrer"
        class="cita-enlace"
      >Ver boletín oficial</a>
    </li>
  </ul>
</template>

<style scoped>
.citas {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.cita {
  font-size: 0.85rem;
  opacity: 0.85;
  border-left: 3px solid var(--color-accent);
  padding-left: 0.6rem;
}

.cita-texto {
  display: block;
}

.cita-enlace {
  color: inherit;
  font-weight: 600;
}
</style>
