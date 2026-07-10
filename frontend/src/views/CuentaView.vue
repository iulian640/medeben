<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useCuentaStore } from '../stores/cuenta'
import { SUBSECTORES } from '../lib/subsectores'
import { pulsoExito } from '../lib/animacion'

const auth = useAuthStore()
const cuenta = useCuentaStore()
const router = useRouter()

/* El aviso "Perfil guardado" recibe el pulso de confirmación justo cuando
 * cuenta.guardado pasa a true: el mismo patrón de la casa (el movimiento
 * marca el instante del éxito, no decora el resto del formulario). */
const confirmacion = ref<HTMLElement | null>(null)

onMounted(() => {
  cuenta.cargar()
})

function onProvincia(event: Event) {
  cuenta.provincia = (event.target as HTMLSelectElement).value
  cuenta.marcarEdicion()
}

function onSubsector(clave: string) {
  cuenta.subsector = clave
  cuenta.marcarEdicion()
}

function onPuesto(event: Event) {
  const valor = (event.target as HTMLSelectElement).value
  cuenta.puestoId = valor === '' ? null : valor
  cuenta.marcarEdicion()
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
  auth.cerrarSesion()
  router.push('/')
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
          :disabled="cuenta.guardando"
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

      <section class="tarjeta">
        <p class="texto-sm texto-suave">
          Tu horario habitual (la semana que se repite) se edita en
          <RouterLink to="/horario">
            Tu horario
          </RouterLink>: es lo que comparamos con tu diario para calcular
          las horas extra.
        </p>
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
</style>
