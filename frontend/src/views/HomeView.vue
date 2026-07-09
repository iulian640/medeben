<script setup lang="ts">
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
</script>

<template>
  <main class="home">
    <h1>TeDeben</h1>
    <p class="tagline">
      Las horas que trabajas, cobradas.
    </p>
    <RouterLink
      class="cta"
      to="/perfil"
    >
      Calcula tu salario mínimo y tus horas extra
    </RouterLink>
    <RouterLink
      v-if="auth.autenticado"
      class="cta"
      to="/resumen"
    >
      ¿Cuánto te deben este mes?
    </RouterLink>
    <RouterLink
      v-if="auth.autenticado"
      class="cta"
      to="/libreta"
    >
      Tu libreta: ficha tu jornada
    </RouterLink>
    <RouterLink
      v-if="auth.autenticado"
      class="secundario"
      to="/cuenta"
    >
      Tu cuenta ({{ auth.email }})
    </RouterLink>
    <RouterLink
      v-else
      class="secundario"
      to="/login"
    >
      Entra o crea tu cuenta para guardar tu perfil
    </RouterLink>
  </main>
</template>

<style scoped>
.home {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  text-align: center;
  padding: 1rem;
}

.tagline {
  font-size: 1.25rem;
  opacity: 0.85;
}

.cta {
  margin-top: 1.5rem;
  display: inline-block;
  padding: 1rem 1.5rem;
  border-radius: 0.75rem;
  background: var(--color-accent);
  color: var(--color-bg);
  text-decoration: none;
  font-weight: 600;
  font-size: 1.05rem;
}

.secundario {
  margin-top: 0.75rem;
  color: var(--color-accent);
  font-size: 0.95rem;
}
</style>
