<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useCuentaStore } from '../stores/cuenta'
import { SUBSECTORES } from '../lib/subsectores'

const auth = useAuthStore()
const cuenta = useCuentaStore()
const router = useRouter()

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
        <p class="email">
          {{ auth.email }}
        </p>
      </div>
      <button
        type="button"
        class="salir"
        @click="salir"
      >
        Cerrar sesión
      </button>
    </header>

    <p
      v-if="cuenta.cargando"
      class="cargando"
      role="status"
      aria-live="polite"
    >
      Cargando tu perfil...
    </p>

    <template v-else>
      <p
        v-if="cuenta.sinPerfil"
        class="aviso"
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
            class="selector"
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

        <fieldset class="campo">
          <legend>¿En qué tipo de sitio?</legend>
          <div class="opciones">
            <button
              v-for="s in SUBSECTORES"
              :key="s.clave"
              type="button"
              class="opcion"
              :class="{ activa: cuenta.subsector === s.clave }"
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
            class="selector"
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
          class="error"
          role="alert"
        >
          {{ cuenta.error }}
        </p>

        <p
          v-if="cuenta.guardado"
          class="confirmacion"
          role="status"
        >
          Perfil guardado.
        </p>

        <button
          type="submit"
          class="principal"
          :disabled="cuenta.guardando"
        >
          {{ cuenta.guardando ? 'Guardando...' : 'Guardar mi perfil' }}
        </button>
      </form>

      <p
        v-if="cuenta.convenioId"
        class="nota-convenio"
      >
        Tu convenio, resuelto por el servidor a partir de provincia y tipo de sitio:
        <strong>{{ cuenta.convenioId }}</strong>. Para ver tu salario mínimo y calcular
        horas extra, usa <RouterLink to="/perfil">
          la calculadora
        </RouterLink>.
      </p>
    </template>
  </main>
</template>

<style scoped>
.cuenta {
  max-width: 480px;
  margin: 0 auto;
  padding: 1.25rem 1rem 3rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.cabecera {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

h1 {
  font-size: 1.5rem;
}

.email {
  opacity: 0.8;
  font-size: 0.9rem;
  overflow-wrap: anywhere;
}

.salir {
  font: inherit;
  font-size: 0.9rem;
  padding: 0.5rem 0.75rem;
  border: 1px solid color-mix(in srgb, var(--color-text) 30%, transparent);
  border-radius: 0.6rem;
  background: var(--color-bg);
  color: var(--color-text);
  cursor: pointer;
  white-space: nowrap;
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
  border: none;
  padding: 0;
  margin: 0;
}

.campo label,
.campo legend {
  font-weight: 600;
  padding: 0;
}

.campo input {
  font: inherit;
  padding: 0.85rem 0.75rem;
  border: 1px solid color-mix(in srgb, var(--color-text) 30%, transparent);
  border-radius: 0.6rem;
  background: var(--color-bg);
  color: var(--color-text);
}

.selector {
  font: inherit;
  padding: 0.85rem 0.75rem;
  border: 1px solid color-mix(in srgb, var(--color-text) 30%, transparent);
  border-radius: 0.6rem;
  background: var(--color-bg);
  color: var(--color-text);
  width: 100%;
}

.opciones {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.opcion {
  font: inherit;
  font-weight: 600;
  text-align: left;
  padding: 0.9rem 1rem;
  border: 1px solid color-mix(in srgb, var(--color-text) 30%, transparent);
  border-radius: 0.6rem;
  background: var(--color-bg);
  color: var(--color-text);
  cursor: pointer;
}

.opcion.activa {
  background: var(--color-accent);
  color: var(--color-bg);
  border-color: var(--color-accent);
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

.confirmacion {
  font-weight: 600;
}

.cargando {
  opacity: 0.7;
  font-size: 0.9rem;
}

.nota-convenio {
  font-size: 0.9rem;
  opacity: 0.85;
}

.nota-convenio a {
  color: var(--color-accent);
}
</style>
