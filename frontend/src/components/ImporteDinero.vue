<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { formatearImporte } from '../lib/formato'
import { cuentaImporte, dibujaTrazo, preparaTrazo } from '../lib/animacion'

/**
 * LA cifra: el importe que le deben al usuario, en tinta y enorme. Cuenta
 * desde 0 al entrar (numerales tabulares: la anchura no baila) y, al llegar,
 * un trazo verde la subraya — el verde marca el dinero, no lo grita.
 *
 * Accesibilidad: el párrafo lleva el valor final en aria-label desde el
 * primer frame; el texto animado va aria-hidden para que un lector de
 * pantalla nunca oiga la cuenta a medias.
 */
const props = defineProps<{
  /** Importe en euros (ya calculado; aquí solo se enseña). */
  importe: number
}>()

const cifra = ref<HTMLElement | null>(null)
const trazo = ref<HTMLElement | null>(null)

async function arranca() {
  if (!cifra.value) {
    return
  }
  if (trazo.value) {
    preparaTrazo(trazo.value)
  }
  await cuentaImporte(cifra.value, props.importe, formatearImporte)
  if (trazo.value) {
    await dibujaTrazo(trazo.value)
  }
}

onMounted(arranca)
watch(() => props.importe, arranca)
</script>

<template>
  <p
    class="importe"
    :aria-label="`${formatearImporte(importe)} euros`"
  >
    <span
      class="linea"
      aria-hidden="true"
    >
      <span
        ref="cifra"
        class="cifra num"
      >{{ formatearImporte(importe) }}</span>
      <span class="euro">€</span>
      <span
        ref="trazo"
        class="trazo"
      />
    </span>
  </p>
</template>

<style scoped>
.importe {
  line-height: 1;
}

.linea {
  position: relative;
  display: inline-flex;
  align-items: baseline;
  gap: 0.35rem;
  padding-bottom: 0.45rem; /* sitio para el trazo sin tocar los descendentes */
}

.cifra {
  font-size: var(--tipo-importe);
  font-weight: var(--peso-importe);
  letter-spacing: -0.02em;
  color: var(--tinta);
}

.euro {
  /* Medio escalón bajo la cifra: el rol "título" de la escala. */
  font-size: var(--tipo-titulo);
  font-weight: var(--peso-titulo);
  color: var(--tinta-suave);
}

.trazo {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 0.25rem;
  border-radius: var(--radio-pastilla);
  background: var(--verde);
  transform-origin: left center;
}
</style>
