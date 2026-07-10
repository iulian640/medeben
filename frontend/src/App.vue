<script setup lang="ts">
import BarraNavegacion from './components/BarraNavegacion.vue'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
</script>

<template>
  <!-- Con la barra inferior visible, el contenido reserva su hueco. -->
  <div :class="{ 'con-barra': auth.autenticado }">
    <!-- Transición entre pantallas: un respiro corto (clases en style.css),
         que prefers-reduced-motion apaga junto al resto. -->
    <RouterView v-slot="{ Component }">
      <Transition
        name="vista"
        mode="out-in"
      >
        <component :is="Component" />
      </Transition>
    </RouterView>
  </div>
  <BarraNavegacion />
</template>

<style scoped>
.con-barra {
  padding-bottom: calc(var(--altura-nav) + env(safe-area-inset-bottom, 0px));
}
</style>
