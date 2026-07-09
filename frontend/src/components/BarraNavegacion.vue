<script setup lang="ts">
import { useAuthStore } from '../stores/auth'

/**
 * Navegación inferior tipo app (solo con sesión): las tres pantallas que un
 * trabajador usa a diario, siempre a un pulgar de distancia. Sin sesión no se
 * pinta — el flujo de entrada (home, login, perfil anónimo) no la necesita.
 */
const auth = useAuthStore()

const pestanas = [
  { a: '/resumen', etiqueta: 'Lo tuyo', icono: '€' },
  { a: '/libreta', etiqueta: 'Libreta', icono: '✓' },
  { a: '/cuenta', etiqueta: 'Cuenta', icono: '●' },
]
</script>

<template>
  <nav
    v-if="auth.autenticado"
    class="barra"
    aria-label="Navegación principal"
  >
    <RouterLink
      v-for="p in pestanas"
      :key="p.a"
      class="pestana"
      :to="p.a"
    >
      <span
        class="icono"
        aria-hidden="true"
      >{{ p.icono }}</span>
      {{ p.etiqueta }}
    </RouterLink>
  </nav>
</template>

<style scoped>
.barra {
  position: fixed;
  inset: auto 0 0 0;
  display: flex;
  height: calc(var(--altura-nav) + env(safe-area-inset-bottom, 0px));
  padding-bottom: env(safe-area-inset-bottom, 0px);
  background: var(--color-bg);
  border-top: 1px solid color-mix(in srgb, var(--color-text) 15%, transparent);
  z-index: 10;
}

.pestana {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.15rem;
  font-size: 0.75rem;
  font-weight: 600;
  text-decoration: none;
  color: color-mix(in srgb, var(--color-text) 65%, transparent);
}

.icono {
  font-size: 1.15rem;
  line-height: 1;
}

/* La pestaña de la pantalla actual, en el color de acción. */
.pestana.router-link-active {
  color: var(--color-accent);
}
</style>
