<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useCuentaStore } from '../stores/cuenta'
import { SUBSECTORES } from '../lib/subsectores'
import { etiquetaDimension, etiquetaValor, explicacionDimension } from '../lib/formato'
import { pulsoExito } from '../lib/animacion'
import PanelUbicacion from '../components/PanelUbicacion.vue'

const auth = useAuthStore()
const cuenta = useCuentaStore()
const router = useRouter()

/*
 * Donación PURA, sin contraprestación: no desbloquea nada ni quita anuncios, así
 * queda exenta de Google Play Billing (la app sigue gratis). El cobro lo procesa
 * Ko-fi (plataforma externa); se abre FUERA del webview (target=_blank + noopener),
 * igual que las citas del BOE.
 *
 * Iulian debe crear la cuenta de Ko-fi con el handle `medeben`; si usa otro,
 * cambiar el handle aquí es una sola línea.
 */
const KOFI_URL = 'https://ko-fi.com/medeben'

/** Una pregunta cada vez, como en la calculadora. */
const siguientePendiente = computed(() => cuenta.pendientesSinResponder[0] ?? null)

/* El aviso "Perfil guardado" recibe el pulso de confirmación justo cuando
 * cuenta.guardado pasa a true: el mismo patrón de la casa (el movimiento
 * marca el instante del éxito, no decora el resto del formulario). */
const confirmacion = ref<HTMLElement | null>(null)

onMounted(() => {
  cuenta.cargar()
})

/* Provincia, tipo de sitio y puesto definen la clasificación en el convenio:
 * cualquier cambio la re-resuelve (y dispara las preguntas encadenadas si el
 * convenio las necesita, igual que la calculadora). */
function onProvincia(event: Event) {
  const valor = (event.target as HTMLSelectElement).value
  if (valor === cuenta.provincia) {
    return
  }
  cuenta.provincia = valor
  cuenta.marcarEdicion()
  cuenta.resuelveClasificacion()
}

function onSubsector(clave: string) {
  if (clave === cuenta.subsector) {
    return
  }
  cuenta.subsector = clave
  cuenta.marcarEdicion()
  cuenta.resuelveClasificacion()
}

function onPuesto(event: Event) {
  const crudo = (event.target as HTMLSelectElement).value
  const valor = crudo === '' ? null : crudo
  if (valor === cuenta.puestoId) {
    return
  }
  cuenta.puestoId = valor
  cuenta.marcarEdicion()
  cuenta.resuelveClasificacion()
}

function onSalario(event: Event) {
  const valor = (event.target as HTMLInputElement).value
  cuenta.salarioBaseMensual = valor === '' ? null : Number(valor)
  cuenta.marcarEdicion()
}

function onPluses(event: Event) {
  const valor = (event.target as HTMLInputElement).value
  cuenta.plusesAnuales = valor === '' ? null : Number(valor)
  cuenta.marcarEdicion()
}

watch(
  () => cuenta.guardado,
  async (guardado) => {
    if (!guardado) {
      return
    }
    await nextTick()
    if (confirmacion.value) {
      pulsoExito(confirmacion.value)
    }
  },
)

function salir() {
  // Fire-and-forget: la memoria se limpia en el acto; la revocación remota y
  // el vaciado del slot compartido siguen bajo el candado en segundo plano.
  void auth.cerrarSesion()
  router.push('/')
}

/* Borrado de cuenta (RGPD art. 17), en dos pasos: primero el aviso con el
 * botón de abrir, y solo entonces el panel con la advertencia final y la
 * contraseña. Borrar destruye la evidencia del usuario: que no pase por un
 * toque de más. */
const borradoAbierto = ref(false)
const passwordBorrado = ref('')
const inputPasswordBorrado = ref<HTMLInputElement | null>(null)
const botonAbrirBorrado = ref<HTMLButtonElement | null>(null)

/* El foco sigue al panel (review de accesibilidad): el botón que lo abre se
 * desmonta del DOM, y sin traslado explícito el foco cae a <body> y un usuario
 * de teclado/lector se queda flotando al principio de la página. */
async function abrirBorrado() {
  auth.limpiarErrorBorrado()
  borradoAbierto.value = true
  await nextTick()
  inputPasswordBorrado.value?.focus()
}

async function cancelarBorrado() {
  auth.limpiarErrorBorrado()
  borradoAbierto.value = false
  passwordBorrado.value = ''
  await nextTick()
  botonAbrirBorrado.value?.focus()
}

async function confirmarBorrado() {
  if (passwordBorrado.value === '' || auth.borrando) {
    return
  }
  const ok = await auth.borrarCuenta(passwordBorrado.value)
  if (ok) {
    router.push('/')
  }
}
</script>

<template>
  <main class="cuenta">
    <header class="cabecera">
      <div>
        <h1>Tu cuenta</h1>
        <p class="email texto-suave texto-sm">
          {{ auth.email }}
        </p>
      </div>
      <button
        type="button"
        class="salir boton-secundario"
        @click="salir"
      >
        Cerrar sesión
      </button>
    </header>

    <p
      v-if="cuenta.cargando"
      class="cargando texto-suave"
      role="status"
      aria-live="polite"
    >
      Cargando tu perfil...
    </p>

    <template v-else>
      <p
        v-if="cuenta.sinPerfil"
        class="texto-suave"
      >
        Todavía no has guardado tu perfil laboral. Rellénalo y lo tendrás siempre a mano.
      </p>

      <form
        novalidate
        @submit.prevent="cuenta.guardar"
      >
        <div class="campo">
          <label for="provincia">¿En qué provincia trabajas?</label>
          <select
            id="provincia"
            :value="cuenta.provincia ?? ''"
            @change="onProvincia"
          >
            <option
              value=""
              disabled
            >
              Elige tu provincia
            </option>
            <option
              v-for="p in cuenta.provincias"
              :key="p"
              :value="p"
            >
              {{ p }}
            </option>
          </select>
        </div>

        <fieldset class="campo campo-subsector">
          <legend>¿En qué tipo de sitio?</legend>
          <div class="opciones">
            <button
              v-for="s in SUBSECTORES"
              :key="s.clave"
              type="button"
              class="opcion boton-secundario"
              :class="{ 'opcion--activa': cuenta.subsector === s.clave }"
              :aria-pressed="cuenta.subsector === s.clave"
              @click="onSubsector(s.clave)"
            >
              {{ s.etiqueta }}
            </button>
          </div>
        </fieldset>

        <div class="campo">
          <label for="puesto">¿De qué trabajas? (opcional)</label>
          <select
            id="puesto"
            :value="cuenta.puestoId ?? ''"
            @change="onPuesto"
          >
            <option value="">
              Sin especificar
            </option>
            <option
              v-for="p in cuenta.puestos"
              :key="p.id"
              :value="p.id"
            >
              {{ p.etiqueta }}
            </option>
          </select>
        </div>

        <!-- La clasificación del puesto en el convenio: sin ella el resumen
             mensual no puede dar cifra. Misma mecánica que la calculadora. -->
        <!-- aria-live en el CONTENEDOR: el lector anuncia también la
             pregunta siguiente cuando aparece, no solo el "consultando". -->
        <div aria-live="polite">
          <p
            v-if="cuenta.resolviendo"
            class="texto-sm texto-suave"
            role="status"
          >
            Consultando tu convenio...
          </p>

          <fieldset
            v-else-if="siguientePendiente"
            class="campo campo-pendiente"
          >
            <legend>
              Una cosa más: ¿{{ etiquetaDimension(siguientePendiente.dimension).toLowerCase() }}?
            </legend>
            <p
              v-if="explicacionDimension(siguientePendiente.dimension)"
              class="campo-ayuda"
            >
              {{ explicacionDimension(siguientePendiente.dimension) }}
            </p>
            <div class="opciones">
              <button
                v-for="valor in siguientePendiente.valores"
                :key="valor"
                type="button"
                class="opcion boton-secundario"
                :aria-pressed="cuenta.respuestas[siguientePendiente.dimension] === valor"
                @click="cuenta.responderPendiente(siguientePendiente.dimension, valor)"
              >
                {{ etiquetaValor(siguientePendiente.dimension, valor) }}
              </button>
            </div>
          </fieldset>

          <p
            v-else-if="cuenta.puestoNoMapeado"
            class="texto-sm texto-suave"
          >
            Este puesto todavía no está clasificado en tu convenio: el perfil se
            guarda igual, pero de momento no podremos estimar tu salario mínimo.
          </p>

          <p
            v-if="cuenta.errorClasificacion"
            class="aviso-bloque"
            role="alert"
          >
            {{ cuenta.errorClasificacion }}
          </p>
        </div>

        <div class="campo">
          <label for="salario">Tu salario base al mes, según tu nómina (opcional)</label>
          <input
            id="salario"
            type="number"
            inputmode="decimal"
            min="0.01"
            step="0.01"
            :value="cuenta.salarioBaseMensual ?? ''"
            @input="onSalario"
          >
        </div>

        <div class="campo">
          <label for="pluses">Pluses al año, si los tienes (opcional)</label>
          <input
            id="pluses"
            type="number"
            inputmode="decimal"
            min="0"
            step="0.01"
            :value="cuenta.plusesAnuales ?? ''"
            @input="onPluses"
          >
        </div>

        <p
          v-if="cuenta.error"
          class="error aviso-bloque"
          role="alert"
        >
          {{ cuenta.error }}
        </p>

        <p
          v-if="cuenta.guardado"
          ref="confirmacion"
          class="confirmacion"
          role="status"
        >
          Perfil guardado.
        </p>

        <button
          type="submit"
          class="boton boton--ancho"
          :disabled="cuenta.guardando || cuenta.resolviendo"
        >
          {{ cuenta.guardando ? 'Guardando...' : 'Guardar mi perfil' }}
        </button>
      </form>

      <section
        v-if="cuenta.convenioId"
        class="tarjeta"
      >
        <p class="texto-sm texto-suave">
          Tu convenio, resuelto por el servidor a partir de provincia y tipo de sitio:
          <strong>{{ cuenta.convenioId }}</strong>. Para ver tu salario mínimo y calcular
          horas extra, usa <RouterLink to="/perfil">
            la calculadora
          </RouterLink>.
        </p>
      </section>

      <section class="tarjeta seccion-horario">
        <h2 class="titulo-seccion">
          Tu horario
        </h2>
        <p class="texto-sm texto-suave">
          Tu semana habitual, la que se repite: es lo que comparamos con tu
          diario para calcular las horas extra.
        </p>
        <RouterLink
          class="boton-secundario boton--ancho"
          to="/horario"
        >
          Editar tu horario
        </RouterLink>
      </section>

      <PanelUbicacion />

      <section class="tarjeta seccion-donacion">
        <h2 class="titulo-seccion">
          Apoyar el proyecto
        </h2>
        <p class="texto-sm texto-suave">
          MeDeben es y será gratis. Si te ha servido y te apetece, puedes
          invitarme a un café. No da acceso a nada extra: es solo apoyo.
        </p>
        <a
          class="boton-secundario boton--ancho enlace-donacion"
          :href="KOFI_URL"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Invítame a un café (se abre en una pestaña nueva)"
        >
          Invítame a un café ☕
        </a>
      </section>

      <section class="tarjeta seccion-borrado">
        <h2 class="titulo-seccion">
          Borrar tu cuenta
        </h2>
        <p class="texto-sm texto-suave">
          Borra tu cuenta y todos tus datos: perfil, horario y tu diario de
          fichajes — tu evidencia. Si has fichado meses, descarga antes el PDF
          de cada mes desde el resumen: esos informes son tu prueba y no se
          pueden recuperar después.
        </p>

        <button
          v-if="!borradoAbierto"
          ref="botonAbrirBorrado"
          type="button"
          class="boton-secundario boton--ancho boton-abrir-borrado"
          @click="abrirBorrado"
        >
          Quiero borrar mi cuenta
        </button>

        <form
          v-else
          class="form-borrado"
          novalidate
          @submit.prevent="confirmarBorrado"
        >
          <!-- role=alert: la frase más importante del flujo también tiene que
               sonar en un lector de pantalla, no solo verse (review a11y). -->
          <p
            class="aviso-bloque"
            role="alert"
          >
            <strong>No hay vuelta atrás:</strong> se borra todo, ahora mismo y
            para siempre. Escribe tu contraseña para confirmar que eres tú.
          </p>
          <div class="campo">
            <label for="password-borrado">Tu contraseña</label>
            <input
              id="password-borrado"
              ref="inputPasswordBorrado"
              v-model="passwordBorrado"
              type="password"
              autocomplete="current-password"
            >
          </div>
          <p
            v-if="auth.errorBorrado"
            class="aviso-bloque"
            role="alert"
          >
            {{ auth.errorBorrado }}
          </p>
          <div class="acciones-borrado">
            <button
              type="button"
              class="boton-secundario boton-cancelar-borrado"
              @click="cancelarBorrado"
            >
              Cancelar
            </button>
            <button
              type="submit"
              class="boton-borrar"
              :disabled="passwordBorrado === '' || auth.borrando"
            >
              {{ auth.borrando ? 'Borrando...' : 'Borrar para siempre' }}
            </button>
          </div>
        </form>
      </section>
    </template>
  </main>
</template>

<style scoped>
.cuenta {
  max-width: 30rem;
  margin: 0 auto;
  padding: var(--esp-lg) var(--esp-md) var(--esp-2xl);
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
}

.cabecera {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--esp-md);
}

.seccion-horario {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}

h1 {
  font-size: var(--tipo-titulo);
}

.email {
  overflow-wrap: anywhere;
}

.salir {
  white-space: nowrap;
}

.cargando {
  padding: var(--esp-xl) 0;
  text-align: center;
}

form {
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
}

/* El fieldset trae borde y relleno de fábrica: se resetean para que se
 * comporte como cualquier otro .campo (etiqueta + control), no como una
 * caja aparte dentro del formulario. */
.campo-subsector {
  border: none;
  padding: 0;
  margin: 0;
}

.campo-subsector legend {
  padding: 0;
  font-size: var(--tipo-sm);
  font-weight: var(--peso-etiqueta);
}

.opciones {
  display: flex;
  flex-direction: column;
  gap: var(--esp-xs);
}

/* Opciones en lista, no en fila: el texto se lee alineado a la izquierda,
 * como en un listado, no centrado como un botón suelto. */
.opcion {
  justify-content: flex-start;
  text-align: left;
}

/* Seleccionado = el mismo verde-suave que usa toda la app para marcar una
 * elección (D..: el verde nunca es decoración, aquí es selección). */
.opcion--activa {
  border-color: var(--verde);
  background: var(--verde-suave);
  color: var(--tinta);
}

.confirmacion {
  font-weight: var(--peso-etiqueta);
}

.seccion-donacion {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}

/* Enlace estilizado como los botones secundarios de la casa (mismo patrón que
 * "Editar tu horario"): el texto va centrado como en un botón. */
.enlace-donacion {
  text-align: center;
}

.seccion-borrado {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}

.form-borrado {
  gap: var(--esp-sm);
}

.acciones-borrado {
  display: flex;
  gap: var(--esp-sm);
}

.acciones-borrado > * {
  flex: 1;
}

/* El único botón rojo de la app: destruir la evidencia no puede vestirse
 * del verde de siempre. Mismo esqueleto que .boton, en --alerta con su
 * pareja --sobre-alerta (review: acoplarlo a --sobre-verde era frágil). */
.boton-borrar {
  border: 1px solid var(--alerta);
  border-radius: var(--radio-control, 8px);
  background: var(--alerta);
  color: var(--sobre-alerta);
  padding: var(--esp-sm) var(--esp-md);
  font: inherit;
  font-weight: var(--peso-etiqueta);
  cursor: pointer;
}

.boton-borrar:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
