<script setup lang="ts">
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
</style>
