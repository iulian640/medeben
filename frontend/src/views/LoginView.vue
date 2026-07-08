<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { destinoTrasLogin } from '../lib/navegacion'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const errorCliente = ref<string | null>(null)

/** Validación básica en cliente; la de verdad la hace el backend. */
const RE_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

async function entrar() {
  errorCliente.value = null
  if (!email.value.trim() || !password.value) {
    errorCliente.value = 'Escribe tu email y tu contraseña.'
    return
  }
  if (!RE_EMAIL.test(email.value.trim())) {
    errorCliente.value = 'Ese email no tiene pinta de email. Revísalo.'
    return
  }
  const ok = await auth.iniciarSesion(email.value.trim(), password.value)
  if (ok) {
    router.push(destinoTrasLogin(route.query.redirect))
  }
}
</script>

<template>
  <main class="auth">
    <h1>Entra en tu cuenta</h1>
    <p class="intro">
      Con cuenta, tu perfil laboral queda guardado y no tienes que repetirlo cada vez.
    </p>

    <p
      v-if="auth.aviso"
      class="aviso"
      role="status"
    >
      {{ auth.aviso }}
    </p>

    <form
      novalidate
      @submit.prevent="entrar"
    >
      <div class="campo">
        <label for="email">Email</label>
        <input
          id="email"
          v-model="email"
          type="email"
          autocomplete="email"
          required
        >
      </div>

      <div class="campo">
        <label for="password">Contraseña</label>
        <input
          id="password"
          v-model="password"
          type="password"
          autocomplete="current-password"
          required
        >
      </div>

      <p
        v-if="errorCliente || auth.error"
        class="error"
        role="alert"
      >
        {{ errorCliente ?? auth.error }}
      </p>

      <button
        type="submit"
        class="principal"
        :disabled="auth.cargando"
      >
        {{ auth.cargando ? 'Entrando...' : 'Entrar' }}
      </button>
    </form>

    <p class="alternativa">
      ¿No tienes cuenta?
      <RouterLink :to="{ name: 'registro', query: route.query }">
        Créala en un minuto
      </RouterLink>
    </p>
  </main>
</template>

<style scoped>
.auth {
  max-width: 480px;
  margin: 0 auto;
  padding: 1.25rem 1rem 3rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

h1 {
  font-size: 1.5rem;
}

.intro {
  opacity: 0.8;
}

form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.campo {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.campo label {
  font-weight: 600;
}

.campo input {
  font: inherit;
  padding: 0.85rem 0.75rem;
  border: 1px solid color-mix(in srgb, var(--color-text) 30%, transparent);
  border-radius: 0.6rem;
  background: var(--color-bg);
  color: var(--color-text);
}

.principal {
  font: inherit;
  font-weight: 600;
  padding: 1rem 1.5rem;
  border: none;
  border-radius: 0.75rem;
  background: var(--color-accent);
  color: var(--color-bg);
  cursor: pointer;
}

.principal:disabled {
  opacity: 0.55;
  cursor: default;
}

.aviso {
  border-left: 3px solid var(--color-accent);
  padding-left: 0.75rem;
  opacity: 0.9;
}

.error {
  color: #c0392b;
}

.alternativa {
  opacity: 0.85;
}

.alternativa a {
  color: var(--color-accent);
}
</style>
