<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { PASSWORD_MAX, PASSWORD_MIN } from '../services/auth'
import { destinoTrasLogin } from '../lib/navegacion'
import { esEmailValido } from '../lib/validacion'
import PanelPlegable from '../components/PanelPlegable.vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const repite = ref('')
const errorCliente = ref<string | null>(null)

/** Detalle RGPD (art. 13) plegado: lo esencial siempre a la vista, el resto a un clic. */
const masDatos = ref(false)

/** Mismos límites que el backend (RegistroRequest): así el error sale al teclear, no tras el viaje. */
function validar(): string | null {
  if (!email.value.trim() || !password.value || !repite.value) {
    return 'Rellena todos los campos.'
  }
  if (!esEmailValido(email.value.trim())) {
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

/*
 * Igual criterio que en LoginView: el error puede venir de cualquiera de
 * los tres campos y el mensaje es uno solo, así que se marca el grupo
 * entero en vez de señalar un campo concreto sin estar seguros.
 */
const hayErrorCampo = computed(() => errorCliente.value !== null || auth.error !== null)
</script>

<template>
  <main class="auth">
    <h1>Crea tu cuenta</h1>
    <p class="texto-suave">
      Solo pedimos un email y una contraseña. Nada más: ni nombre, ni teléfono, ni empresa.
    </p>

    <form
      novalidate
      @submit.prevent="crearCuenta"
    >
      <div
        class="campo"
        :class="{ 'campo--error': hayErrorCampo }"
      >
        <label for="email">Email</label>
        <!-- El estado de error también en aria: el borde rojo solo lo ve quien ve. -->
        <input
          id="email"
          v-model="email"
          type="email"
          autocomplete="email"
          required
          :aria-invalid="hayErrorCampo || undefined"
          :aria-describedby="hayErrorCampo ? 'error-formulario' : undefined"
        >
      </div>

      <div
        class="campo"
        :class="{ 'campo--error': hayErrorCampo }"
      >
        <label for="password">Contraseña</label>
        <input
          id="password"
          v-model="password"
          type="password"
          autocomplete="new-password"
          :minlength="PASSWORD_MIN"
          required
          :aria-invalid="hayErrorCampo || undefined"
          :aria-describedby="hayErrorCampo ? 'error-formulario' : undefined"
        >
        <p class="campo-ayuda">
          Mínimo {{ PASSWORD_MIN }} caracteres. Una frase que recuerdes vale de sobra.
        </p>
      </div>

      <div
        class="campo"
        :class="{ 'campo--error': hayErrorCampo }"
      >
        <label for="repite">Repite la contraseña</label>
        <input
          id="repite"
          v-model="repite"
          type="password"
          autocomplete="new-password"
          required
          :aria-invalid="hayErrorCampo || undefined"
          :aria-describedby="hayErrorCampo ? 'error-formulario' : undefined"
        >
      </div>

      <p
        v-if="errorCliente || auth.error"
        id="error-formulario"
        class="campo-error"
        role="alert"
      >
        {{ errorCliente ?? auth.error }}
      </p>

      <button
        type="submit"
        class="boton boton--ancho"
        :disabled="auth.cargando"
      >
        {{ auth.cargando ? 'Creando cuenta...' : 'Crear cuenta' }}
      </button>

      <!-- No es un consentimiento (la base es 6.1.b, ejecución del servicio):
           es el aviso de aceptación de los Términos, no un checkbox. -->
      <p class="terminos-nota texto-xs texto-suave">
        Al crear la cuenta aceptas los
        <RouterLink to="/terminos">
          Términos
        </RouterLink>.
      </p>
    </form>

    <!-- Información al interesado (RGPD art. 13): lo esencial en llano, siempre
         visible antes de crear la cuenta; el resto plegado y la política entera
         a un enlace. La ruta /privacidad la sirve otra rama: se enlaza por path. -->
    <section
      class="rgpd tarjeta"
      aria-labelledby="rgpd-titulo"
    >
      <h2
        id="rgpd-titulo"
        class="titulo-seccion"
      >
        Qué hacemos con tus datos
      </h2>
      <p class="texto-sm">
        Tu email, tu contraseña y lo que apuntes de tu jornada los trata
        <strong>Iulian Timofei</strong> con un solo fin: que la app te sirva
        —registrar tu jornada, calcular lo que pudieran deberte y generarte tu
        informe—. Se guardan mientras tengas la cuenta; si la borras, se borra
        todo de verdad.
      </p>

      <button
        type="button"
        class="boton-fantasma rgpd-mas"
        :aria-expanded="masDatos"
        aria-controls="detalle-rgpd"
        @click="masDatos = !masDatos"
      >
        {{ masDatos ? 'Menos sobre tus datos' : 'Más sobre tus datos' }}
      </button>

      <!-- El id cae por attrs en la raíz del panel: es lo que apunta aria-controls. -->
      <PanelPlegable
        id="detalle-rgpd"
        :abierto="masDatos"
      >
        <dl class="rgpd-detalle texto-sm">
          <dt>Responsable</dt>
          <dd>Iulian Timofei.</dd>

          <dt>Para qué</dt>
          <dd>
            Registrar tu jornada, calcular lo que pudieran deberte y generar tu
            informe.
          </dd>

          <dt>Base legal</dt>
          <dd>
            Ejecutar el servicio que nos pides (art. 6.1.b RGPD). Si anotas el
            motivo de una ausencia —que puede hablar de tu salud—, lo tratamos
            para poder defender una reclamación (art. 9.2.f RGPD).
          </dd>

          <dt>Cuánto tiempo</dt>
          <dd>
            Mientras mantengas la cuenta. Si la borras, se borra todo de verdad.
          </dd>

          <dt>Tus derechos</dt>
          <dd>
            Acceder a tus datos, rectificarlos, borrarlos, llevártelos
            (portabilidad), limitar u oponerte a su tratamiento. Y reclamar ante
            la AEPD, la autoridad de protección de datos.
          </dd>
        </dl>
      </PanelPlegable>

      <p class="texto-sm">
        <RouterLink to="/privacidad">
          Política de privacidad completa
        </RouterLink>
      </p>
    </section>

    <p class="texto-sm texto-suave">
      ¿Ya tienes cuenta?
      <RouterLink :to="{ name: 'login', query: route.query }">
        Entra
      </RouterLink>
    </p>
  </main>
</template>

<style scoped>
/* Layout de vista estándar (DESIGN.md): columna centrada, ritmo apretado
 * dentro de un grupo y generoso entre bloques. */
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

form {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}

/* La nota de Términos va pegada al botón, no como un campo más. */
.terminos-nota {
  margin-top: var(--esp-2xs);
  text-align: center;
}

/* Bloque de información RGPD: tarjeta compacta, su propio ritmo interno.
 * --plegable-compensa-gap = el gap de la tarjeta, para que plegado no deje
 * doble hueco (contrato de PanelPlegable). */
.rgpd {
  --plegable-compensa-gap: var(--esp-xs);
  gap: var(--esp-sm);
}

/* El disparador parece un enlace pero con área táctil de botón; alineado a la
 * izquierda como el texto, no centrado como una acción principal. */
.rgpd-mas {
  align-self: flex-start;
  padding-inline: 0;
  min-height: auto;
}

.rgpd-detalle {
  display: grid;
  gap: var(--esp-2xs) var(--esp-sm);
  margin: 0;
}

.rgpd-detalle dt {
  font-weight: var(--peso-etiqueta);
}

.rgpd-detalle dd {
  margin: 0 0 var(--esp-xs);
  color: var(--tinta-suave);
}

.rgpd-detalle dd:last-child {
  margin-bottom: 0;
}
</style>
