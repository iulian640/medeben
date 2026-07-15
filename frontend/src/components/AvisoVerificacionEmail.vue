<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'

/**
 * Aviso "confirma tu correo" (verificación de email, B4): visible SOLO con
 * sesión y con emailVerificado en false explícito — nunca con null (estado
 * aún desconocido) ni con true. Vive en App.vue (layout principal) para
 * seguir al usuario a cualquier pantalla mientras la cuenta siga sin
 * confirmar; las cuentas sin verificar usan la app con normalidad (decisión
 * de producto), esto es solo un recordatorio, no un bloqueo.
 *
 * El estado se pide siempre fresco a /me (auth.actualizarEstadoVerificacion):
 * en una tablet compartida, el aviso del usuario anterior no puede quedarse
 * pegado para el siguiente que inicia sesión. Se pide al montar (por si ya
 * hay sesión, p. ej. tras una recarga) y de nuevo cada vez que autenticado
 * pasa de false a true (login explícito dentro de esta misma carga de página).
 */
const auth = useAuthStore()

const reenviando = ref(false)
const reenviado = ref(false)

onMounted(() => {
  void auth.actualizarEstadoVerificacion()
})

watch(
  () => auth.autenticado,
  (ahora, antes) => {
    if (ahora && !antes) {
      void auth.actualizarEstadoVerificacion()
    }
  },
)

async function reenviar() {
  if (auth.email === null || reenviando.value) {
    return
  }
  reenviando.value = true
  reenviado.value = false
  await auth.reenviarVerificacion(auth.email)
  reenviando.value = false
  reenviado.value = true
}
</script>

<template>
  <p
    v-if="auth.autenticado && auth.emailVerificado === false"
    class="aviso-bloque aviso-verificacion"
    role="status"
    aria-live="polite"
  >
    <span>Confirma tu correo para proteger tu cuenta.</span>
    <button
      type="button"
      class="boton-fantasma"
      :disabled="reenviando"
      @click="reenviar"
    >
      {{ reenviado ? 'Correo enviado' : reenviando ? 'Enviando...' : 'Reenviar' }}
    </button>
  </p>
</template>

<style scoped>
.aviso-verificacion {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--esp-sm);
  margin: var(--esp-sm);
}
</style>
