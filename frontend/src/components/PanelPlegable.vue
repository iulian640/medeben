<script setup lang="ts">
/**
 * Despliegue inline con animación: la alternativa de la casa al modal.
 * El contenido se abre debajo de su disparador (el usuario no pierde el
 * contexto) usando grid-template-rows 0fr→1fr, que reordena el layout sin
 * animar height a pelo.
 *
 * El padre controla `abierto` (los botones que abren/cierran viven fuera).
 * Cerrado, el contenido queda inerte: ni foco ni lectores entran en él.
 */
defineProps<{
  abierto: boolean
}>()
</script>

<template>
  <div
    class="plegable"
    :class="{ abierto }"
    :inert="!abierto || undefined"
  >
    <div class="recorte">
      <div class="contenido">
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.plegable {
  display: grid;
  grid-template-rows: 0fr;
  transition:
    grid-template-rows var(--dur-panel) var(--curva-salida),
    margin-top var(--dur-panel) var(--curva-salida);
}

/*
 * CONTRATO PÚBLICO de estilo: cerrado, el panel mide 0 pero sigue ocupando
 * el gap de la columna del padre. El padre que quiera compensar ese hueco
 * define --plegable-compensa-gap con SU gap; sin definirla no pasa nada.
 * (Si renombras la variable, busca a sus consumidores: es API.)
 */
.plegable:not(.abierto) {
  margin-top: calc(-1 * var(--plegable-compensa-gap, 0px));
}

.plegable.abierto {
  grid-template-rows: 1fr;
}

.recorte {
  overflow: hidden;
  min-height: 0;
}

.contenido {
  opacity: 0;
  transform: translateY(-4px);
  transition:
    opacity var(--dur-estado) var(--curva-suave),
    transform var(--dur-panel) var(--curva-salida);
}

.abierto .contenido {
  opacity: 1;
  transform: none;
  /* La opacidad espera a que el hueco casi exista; al cerrar va primero. */
  transition-delay: 80ms;
}

@media (prefers-reduced-motion: reduce) {
  .abierto .contenido {
    transition-delay: 0ms;
  }
}
</style>
