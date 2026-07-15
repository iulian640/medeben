<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { mensajeDeError } from '../lib/formato'
import { esEmailValido } from '../lib/validacion'

/**
 * Pantalla PÚBLICA del enlace de verificación (?token=...). Quien la abre
 * puede no tener sesión en ESTE navegador (otro dispositivo, o cerró sesión),
 * así que el reenvío de aquí pide el email a mano en vez de asumir auth.email.
 *
 * El POST se dispara UNA sola vez al montar, con el token que traía la URL en
 * ese momento: no hay ningún watcher sobre la query, así que un cambio
 * posterior de la ruta no repite la llamada.
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

type Estado = 'comprobando' | 'ok' | 'error'
const estado = ref<Estado>('comprobando')
const mensajeError = ref('')

const emailReenvio = ref('')
const errorReenvio = ref<string | null>(null)
const reenviando = ref(false)
const reenviado = ref(false)

onMounted(async () => {
  const token = route.query.token
  if (typeof token !== 'string' || token.length === 0) {
    estado.value = 'error'
    mensajeError.value = 'Este enlace no es válido. Revisa que lo has abierto completo.'
    return
  }
  // Fuera de la URL cuanto antes, ANTES de canjearlo: un token de un solo uso no
  // debe quedar en el history de una tablet compartida ni filtrarse por la
  // cabecera Referer (que además apagamos con <meta referrer> en index.html).
  // El token ya está capturado en la constante local, así que quitarlo de la
  // query no afecta a la verificación de abajo.
  await router.replace({ query: {} })
  try {
    await auth.verificarEmail(token)
    estado.value = 'ok'
  } catch (e) {
    estado.value = 'error'
    mensajeError.value = mensajeDeError(e)
  }
})

async function reenviar() {
  errorReenvio.value = null
  if (!emailReenvio.value.trim() || !esEmailValido(emailReenvio.value.trim())) {
    errorReenvio.value = 'Escribe el email de tu cuenta para reenviarte el enlace.'
    return
  }
  reenviando.value = true
  reenviado.value = false
  await auth.reenviarVerificacion(emailReenvio.value.trim())
  reenviando.value = false
  reenviado.value = true
}
</script>

<template>
  <main class="auth">
    <h1>Verificación de tu email</h1>

    <p
      v-if="estado === 'comprobando'"
      class="texto-suave"
    >
      Comprobando el enlace...
    </p>

    <section
      v-else-if="estado === 'ok'"
      class="tarjeta resultado"
      role="status"
      aria-live="polite"
    >
      <h2 class="titulo-seccion">
        Cuenta verificada
      </h2>
      <p>Tu email queda confirmado. Ya puedes entrar con normalidad.</p>
      <RouterLink
        to="/login"
        class="boton boton--ancho"
      >
        Iniciar sesión
      </RouterLink>
    </section>

    <template v-else>
      <p
        role="alert"
        class="campo-error"
      >
        {{ mensajeError }}
      </p>

      <section class="tarjeta seccion-form">
        <h2 class="titulo-seccion">
          Pide un enlace nuevo
        </h2>
        <p class="texto-sm texto-suave">
          Escribe el email de tu cuenta y te mandamos un correo de verificación nuevo.
        </p>

        <form
          novalidate
          @submit.prevent="reenviar"
        >
          <div
            class="campo"
            :class="{ 'campo--error': errorReenvio }"
          >
            <label for="email-reenvio">Email</label>
            <input
              id="email-reenvio"
              v-model="emailReenvio"
              type="email"
              autocomplete="email"
              required
              :aria-invalid="!!errorReenvio || undefined"
            >
          </div>

          <p
            v-if="errorReenvio"
            role="alert"
            class="campo-error"
          >
            {{ errorReenvio }}
          </p>

          <button
            type="submit"
            class="boton-secundario boton--ancho"
            :disabled="reenviando"
          >
            {{ reenviando ? 'Enviando...' : 'Reenviar correo' }}
          </button>
        </form>

        <p
          v-if="reenviado"
          class="aviso-bloque"
          role="status"
          aria-live="polite"
        >
          Si la cuenta existe y aún no está verificada, te hemos enviado un
          nuevo correo.
        </p>
      </section>
    </template>
  </main>
</template>

<style scoped>
.auth {
  max-width: 30rem;
  margin: 0 auto;
  padding: var(--esp-lg) var(--esp-md) var(--esp-2xl);
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
}

h1 {
  font-size: var(--tipo-titulo);
}

.resultado,
.seccion-form {
  gap: var(--esp-sm);
}

form {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}
</style>
