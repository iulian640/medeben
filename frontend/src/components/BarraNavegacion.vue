<script setup lang="ts">
import { useAuthStore } from '../stores/auth'

/**
 * Navegación inferior tipo app (solo con sesión): las tres pantallas que un
 * trabajador usa a diario, siempre a un pulgar de distancia. Sin sesión no se
 * pinta — el flujo de entrada (home, login, perfil anónimo) no la necesita.
 */
const auth = useAuthStore()

/**
 * Iconos de línea (SVG inline, currentColor): heredan el color de la pestaña,
 * así que la activa se tiñe de acento sin lógica extra. Trazo geométrico
 * simple para que se lean nítidos a 22px en cualquier densidad.
 */
const pestanas = [
  {
    a: '/resumen',
    etiqueta: 'Lo tuyo',
    // Euro
    icono: 'M17 6.5a6 6 0 1 0 0 11 M4 10h9 M4 13.5h9',
  },
  {
    a: '/libreta',
    etiqueta: 'Libreta',
    // Libreta con renglones y anilla
    icono: 'M6 4h11a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z M9 8h6 M9 12h6 M9 16h3 M8 3v3',
  },
  {
    a: '/cuenta',
    etiqueta: 'Cuenta',
    // Persona
    icono: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M5 20a7 7 0 0 1 14 0',
  },
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
      <svg
        class="icono"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path :d="p.icono" />
      </svg>
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
  width: 1.4rem;
  height: 1.4rem;
}

/* La pestaña de la pantalla actual, en el color de acción. */
.pestana.router-link-active {
  color: var(--color-accent);
}
</style>
