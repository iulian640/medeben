<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()

/*
 * Sin sesión, calcular el salario es el único camino posible: hace de
 * entrada (.boton). Con sesión, la pregunta del dinero pasa a ser la
 * protagonista (D1) y este enlace baja a acción de apoyo. Nunca hay dos
 * .boton a la vez en pantalla (DESIGN.md).
 *
 * Sin animación de entrada: revelaEscalonado es para contenido recién
 * cargado, y estos CTAs son estáticos — animarlos sería coreografía de
 * carga de página, prohibida por DESIGN.md (hallazgo de la review).
 */
const claseCalculadora = computed(() => (auth.autenticado ? 'boton-secundario' : 'boton'))
</script>

<template>
  <main class="home">
    <div class="marca">
      <h1>MeDeben</h1>
      <p class="tagline texto-suave">
        Registra tus horas trabajadas
      </p>
    </div>

    <div class="ctas">
      <RouterLink
        class="boton--ancho"
        :class="claseCalculadora"
        to="/perfil"
      >
        Calcula tu salario mínimo y tus horas extra
      </RouterLink>
      <RouterLink
        v-if="auth.autenticado"
        class="boton boton--ancho"
        to="/resumen"
      >
        ¿Cuánto te deben este mes?
      </RouterLink>
      <RouterLink
        v-if="auth.autenticado"
        class="boton-secundario boton--ancho"
        to="/libreta"
      >
        Tu libreta: ficha tu jornada
      </RouterLink>
      <RouterLink
        v-if="auth.autenticado"
        class="boton-fantasma"
        to="/cuenta"
      >
        Tu cuenta ({{ auth.email }})
      </RouterLink>
      <RouterLink
        v-else
        class="boton-fantasma"
        to="/login"
      >
        Entra o crea tu cuenta para guardar tu perfil
      </RouterLink>
    </div>
  </main>
</template>

<style scoped>
.home {
  min-height: 100vh;
  max-width: 30rem;
  margin-inline: auto;
  padding: var(--esp-lg) var(--esp-md) var(--esp-2xl);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--esp-2xl);
  text-align: center;
}

.marca {
  display: flex;
  flex-direction: column;
  gap: var(--esp-xs);
}

/* El único sitio de la app donde vive el nombre de marca (la barra inferior
 * no lleva logo): se le da el mismo peso visual que LA cifra del dinero,
 * pero en tinta — el verde es solo para el dinero y la acción, nunca la marca. */
.marca h1 {
  font-size: var(--tipo-importe);
  font-weight: var(--peso-importe);
  letter-spacing: -0.02em;
  line-height: 1;
}

.tagline {
  font-size: var(--tipo-xl);
  text-wrap: balance;
}

.ctas {
  display: flex;
  flex-direction: column;
  align-items: center; /* solo estiran ancho los .boton--ancho; el enlace fantasma queda ligero */
  gap: var(--esp-sm);
}
</style>
