<script setup lang="ts">
import BarraNavegacion from './components/BarraNavegacion.vue'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
</script>

<template>
  <!-- Con la barra inferior visible, el contenido reserva su hueco. -->
  <div :class="{ 'con-barra': auth.autenticado }">
    <!-- Transición entre pantallas: un respiro corto (clases en style.css),
         que prefers-reduced-motion apaga junto al resto. La duración va
         explícita a propósito: con ella Vue cierra la transición por timer
         en vez de esperar un transitionend que, con el webview en segundo
         plano (rendering parado), puede no llegar — y con mode="out-in" eso
         dejaría la pantalla en blanco. Mismos valores que --dur-estado y
         --dur-vista-salida en style.css. -->
    <RouterView v-slot="{ Component }">
      <Transition
        name="vista"
        mode="out-in"
        :duration="{ enter: 200, leave: 140 }"
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
