<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

/**
 * Pantalla "revisa tu correo" tras el registro (verificación de email, B4).
 * El registro YA NO inicia sesión: la cuenta queda creada pero sin verificar,
 * así que aquí no hay nada más que hacer salvo esperar el correo — o entrar
 * igualmente: las cuentas sin verificar usan la app con normalidad (decisión
 * de producto); el aviso "confirma tu correo" seguirá recordándoselo dentro.
 *
 * Depende del email que el registro acaba de guardar en el store
 * (emailRecienRegistrado, solo en memoria de esta pestaña). Sin él —entrada
 * directa a la URL, recarga— no hay nada que enseñar: se manda de vuelta a
 * /registro en vez de fingir un estado a medias.
 */
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const reenviando = ref(false)
const reenviado = ref(false)

onMounted(() => {
  if (auth.emailRecienRegistrado === null) {
    router.replace({ name: 'registro' })
  }
})

onUnmounted(() => {
  // Al abandonar la pantalla el email de registro deja de hacer falta: no lo
  // dejamos vivo para que el siguiente usuario (tablet compartida, botón atrás)
  // no vea una dirección ajena. El login explícito también lo limpia, pero
  // esto cubre irse a cualquier otro sitio sin haber entrado.
  auth.emailRecienRegistrado = null
})

async function reenviar() {
  if (auth.emailRecienRegistrado === null || reenviando.value) {
    return
  }
  reenviando.value = true
  reenviado.value = false
  await auth.reenviarVerificacion(auth.emailRecienRegistrado)
  reenviando.value = false
  reenviado.value = true
}
</script>

<template>
  <main
    v-if="auth.emailRecienRegistrado"
    class="auth"
  >
    <h1>Revisa tu correo</h1>
    <p class="texto-suave">
      Hemos enviado un correo de verificación a
      <strong>{{ auth.emailRecienRegistrado }}</strong>. Abre el enlace para
      confirmar tu cuenta — caduca en 24 horas.
    </p>

    <p class="texto-sm texto-suave">
      No hace falta esperar: puedes usar la app mientras tanto. Solo te lo
      recordaremos hasta que lo confirmes.
    </p>

    <button
      type="button"
      class="boton-secundario boton--ancho"
      :disabled="reenviando"
      @click="reenviar"
    >
      {{ reenviando ? 'Enviando...' : '¿No te ha llegado? Reenviar correo' }}
    </button>

    <p
      v-if="reenviado"
      class="aviso-bloque"
      role="status"
      aria-live="polite"
    >
      Si la cuenta existe y aún no está verificada, te hemos enviado un
      nuevo correo.
    </p>

    <p class="texto-sm texto-suave">
      <!-- Conserva el ?redirect= del deep-link: si el usuario venía de una ruta
           protegida, tras entrar aterriza donde quería (no se pierde aquí). -->
      <RouterLink :to="{ name: 'login', query: route.query }">
        Iniciar sesión
      </RouterLink>
    </p>
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
</style>
