<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { esEmailValido } from '../lib/validacion'
import EnlacesLegales from '../components/EnlacesLegales.vue'

/**
 * Página PÚBLICA de borrado de cuenta (la exige Google Play: una URL para pedir
 * el borrado aunque ya no se tenga la app). Reutiliza la lógica de auth de la
 * casa, sin duplicarla:
 *  - `auth.iniciarSesion` para reautenticar (mismo login que la app), y
 *  - `auth.borrarCuenta`, que llama al DELETE /cuenta reconfirmando la contraseña.
 * El correo (art. 17 RGPD) es la vía alternativa por si no recuerda la contraseña.
 */
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const errorCliente = ref<string | null>(null)
/** Error de la ejecución (credenciales, red): copia del mensaje en castellano
 *  que produce el store, aislado para no arrastrar estado del store en pantalla. */
const errorEjecucion = ref<string | null>(null)
/** Segundo paso: la confirmación final antes de ejecutar algo irreversible. */
const confirmando = ref(false)
/** Resultado: la cuenta ya se ha borrado. */
const borrado = ref(false)

const CORREO = 'iuliantim21@gmail.com'
const mailto = computed(() => {
  const asunto = encodeURIComponent('Solicitud de borrado de cuenta (MeDeben)')
  const cuerpo = encodeURIComponent(
    'Hola:\n\nSolicito el borrado definitivo de mi cuenta de MeDeben y de todos ' +
      'mis datos (perfil, fichajes, ausencias e informes). Escribo desde el correo ' +
      'de mi cuenta.\n\nGracias.',
  )
  return `mailto:${CORREO}?subject=${asunto}&body=${cuerpo}`
})

const mensajeError = computed(() => errorCliente.value ?? errorEjecucion.value)
const hayError = computed(() => mensajeError.value !== null)
const ocupado = computed(() => auth.cargando || auth.borrando)

function validar(): string | null {
  if (!email.value.trim() || !password.value) {
    return 'Escribe tu email y tu contraseña.'
  }
  if (!esEmailValido(email.value.trim())) {
    return 'Ese email no tiene pinta de email. Revísalo.'
  }
  return null
}

/** Paso 1 → paso 2: solo valida en cliente, NO toca la red todavía. */
function continuar() {
  errorEjecucion.value = null
  errorCliente.value = validar()
  if (errorCliente.value) {
    return
  }
  confirmando.value = true
}

function cancelar() {
  confirmando.value = false
  errorCliente.value = null
  errorEjecucion.value = null
}

/** Al editar tras un fallo, se limpia el error para no dejarlo pegado. */
function alEditar() {
  errorCliente.value = null
  errorEjecucion.value = null
}

async function ejecutarBorrado() {
  if (ocupado.value) {
    return
  }
  errorCliente.value = null
  errorEjecucion.value = null
  const entro = await auth.iniciarSesion(email.value.trim(), password.value)
  if (!entro) {
    errorEjecucion.value = auth.error ?? 'No hemos podido entrar con ese email y esa contraseña.'
    return
  }
  const ok = await auth.borrarCuenta(password.value)
  if (!ok) {
    errorEjecucion.value =
      auth.errorBorrado ?? 'No se ha podido borrar la cuenta. Inténtalo de nuevo.'
    return
  }
  borrado.value = true
}
</script>

<template>
  <main class="borrar">
    <h1>Borrar tu cuenta</h1>

    <section
      v-if="borrado"
      class="tarjeta resultado"
      role="status"
      aria-live="polite"
    >
      <h2 class="titulo-seccion">
        Cuenta borrada
      </h2>
      <p>
        Tu cuenta y todos tus datos se han borrado de forma definitiva. No queda
        ninguna copia.
      </p>
      <RouterLink
        to="/"
        class="boton boton--ancho"
      >
        Volver al inicio
      </RouterLink>
    </section>

    <template v-else>
      <p class="texto-suave">
        Desde aquí puedes borrar tu cuenta de MeDeben aunque ya no tengas la app
        instalada. El borrado es <strong>definitivo</strong>.
      </p>

      <section class="tarjeta">
        <h2 class="titulo-seccion">
          Qué se borra
        </h2>
        <p class="texto-sm">
          Se borra <strong>todo</strong>, de forma definitiva y sin copia oculta:
        </p>
        <ul class="lista-datos texto-sm">
          <li>tu <strong>cuenta</strong> (email y contraseña),</li>
          <li>
            tu <strong>perfil</strong> laboral (provincia, convenio, puesto y
            salario),
          </li>
          <li>tus <strong>fichajes</strong> y tu horario,</li>
          <li>tus <strong>ausencias</strong> y sus motivos,</li>
          <li>
            la <strong>ubicación</strong> de tus fichajes, si activaste
            «Anotar dónde fichas»,
          </li>
          <li>tus <strong>informes</strong>.</li>
        </ul>
        <p
          class="aviso-bloque"
          role="note"
        >
          <strong>Descarga antes tu informe en PDF</strong> si lo necesitas: es
          tu prueba y no se puede recuperar después.
        </p>
      </section>

      <section class="tarjeta seccion-form">
        <h2 class="titulo-seccion">
          Opción 1: bórrala ahora
        </h2>
        <p class="texto-sm texto-suave">
          Entra con tu email y tu contraseña y confirma el borrado.
        </p>

        <form
          novalidate
          @submit.prevent="continuar"
        >
          <div
            class="campo"
            :class="{ 'campo--error': hayError }"
          >
            <label for="email-borrado">Email</label>
            <input
              id="email-borrado"
              v-model="email"
              type="email"
              autocomplete="email"
              required
              :aria-invalid="hayError || undefined"
              :aria-describedby="hayError ? 'error-borrado' : undefined"
              @input="alEditar"
            >
          </div>

          <div
            class="campo"
            :class="{ 'campo--error': hayError }"
          >
            <label for="password-borrado">Contraseña</label>
            <input
              id="password-borrado"
              v-model="password"
              type="password"
              autocomplete="current-password"
              required
              :aria-invalid="hayError || undefined"
              :aria-describedby="hayError ? 'error-borrado' : undefined"
              @input="alEditar"
            >
          </div>

          <p
            v-if="mensajeError"
            id="error-borrado"
            class="campo-error"
            role="alert"
          >
            {{ mensajeError }}
          </p>

          <button
            v-if="!confirmando"
            type="submit"
            class="boton-secundario boton--ancho boton-continuar"
          >
            Continuar
          </button>

          <template v-else>
            <p
              class="aviso-bloque"
              role="alert"
            >
              <strong>No hay vuelta atrás:</strong> esto borra TODOS tus datos de
              forma definitiva, ahora mismo. Descarga antes tu informe PDF si lo
              necesitas.
            </p>
            <div class="acciones">
              <button
                type="button"
                class="boton-secundario boton-cancelar"
                @click="cancelar"
              >
                Cancelar
              </button>
              <button
                type="button"
                class="boton-borrar"
                :disabled="ocupado"
                @click="ejecutarBorrado"
              >
                {{ ocupado ? 'Borrando...' : 'Borrar para siempre' }}
              </button>
            </div>
          </template>
        </form>
      </section>

      <section class="tarjeta">
        <h2 class="titulo-seccion">
          Opción 2: pídelo por email
        </h2>
        <p class="texto-sm texto-suave">
          Si no recuerdas la contraseña, escribe <strong>desde el correo de tu
            cuenta</strong> a
          <a :href="`mailto:${CORREO}`">{{ CORREO }}</a> pidiendo el borrado.
          Responderemos en el plazo de <strong>un mes</strong> y borraremos todos
          tus datos, sin copia oculta.
        </p>
        <a
          class="boton-secundario boton--ancho"
          :href="mailto"
        >
          Escribir el email
        </a>
      </section>

      <EnlacesLegales />
    </template>
  </main>
</template>

<style scoped>
.borrar {
  max-width: 32rem;
  margin: 0 auto;
  padding: var(--esp-lg) var(--esp-md) var(--esp-2xl);
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
}

h1 {
  font-size: var(--tipo-titulo);
}

.seccion-form,
.resultado {
  gap: var(--esp-sm);
}

.lista-datos {
  margin: 0;
  padding-left: var(--esp-lg);
  display: flex;
  flex-direction: column;
  gap: var(--esp-2xs);
}

form {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}

.acciones {
  display: flex;
  gap: var(--esp-sm);
}

.acciones > * {
  flex: 1;
}

/* El botón destructivo: mismo rojo de alerta que el borrado dentro de la app,
 * nunca el verde de la casa (destruir no se viste de acción positiva). */
.boton-borrar {
  border: 1px solid var(--alerta);
  border-radius: var(--radio-control);
  background: var(--alerta);
  color: var(--sobre-alerta);
  min-height: 2.75rem;
  padding: 0.5rem 1.25rem;
  font: inherit;
  font-weight: var(--peso-etiqueta);
  cursor: pointer;
}

.boton-borrar:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
