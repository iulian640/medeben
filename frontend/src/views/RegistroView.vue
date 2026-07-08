<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { PASSWORD_MAX, PASSWORD_MIN } from '../services/auth'
import { destinoTrasLogin } from '../lib/navegacion'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const repite = ref('')
const errorCliente = ref<string | null>(null)

const RE_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** Mismos límites que el backend (RegistroRequest): así el error sale al teclear, no tras el viaje. */
function validar(): string | null {
  if (!email.value.trim() || !password.value || !repite.value) {
    return 'Rellena todos los campos.'
  }
  if (!RE_EMAIL.test(email.value.trim())) {
    return 'Ese email no tiene pinta de email. Revísalo.'
  }
  if (password.value.length < PASSWORD_MIN) {
    return `La contraseña necesita al menos ${PASSWORD_MIN} caracteres. Truco: una frase corta vale.`
  }
  if (password.value.length > PASSWORD_MAX) {
    return `La contraseña no puede pasar de ${PASSWORD_MAX} caracteres.`
  }
  if (password.value !== repite.value) {
    return 'Las contraseñas no coinciden.'
  }
  return null
}

async function crearCuenta() {
  errorCliente.value = validar()
  if (errorCliente.value) {
    return
  }
  const ok = await auth.registrarse(email.value.trim(), password.value)
  if (ok) {
    router.push(destinoTrasLogin(route.query.redirect))
  }
}
</script>

<template>
  <main class="auth">
    <h1>Crea tu cuenta</h1>
    <p class="intro">
      Solo pedimos un email y una contraseña. Nada más: ni nombre, ni teléfono, ni empresa.
    </p>

    <form
      novalidate
      @submit.prevent="crearCuenta"
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
          autocomplete="new-password"
          :minlength="PASSWORD_MIN"
          required
        >
        <p class="ayuda">
          Mínimo {{ PASSWORD_MIN }} caracteres. Una frase que recuerdes vale de sobra.
        </p>
      </div>

      <div class="campo">
        <label for="repite">Repite la contraseña</label>
        <input
          id="repite"
          v-model="repite"
          type="password"
          autocomplete="new-password"
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
        {{ auth.cargando ? 'Creando cuenta...' : 'Crear cuenta' }}
      </button>
    </form>

    <p class="alternativa">
      ¿Ya tienes cuenta?
      <RouterLink :to="{ name: 'login', query: route.query }">
        Entra
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

.ayuda {
  font-size: 0.85rem;
  opacity: 0.75;
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
