<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { usePerfilStore } from '../stores/perfil'
import { SUBSECTORES } from '../lib/subsectores'
import {
  etiquetaDimension,
  etiquetaUnidad,
  explicacionDimension,
  formatearFecha,
  formatearImporte,
} from '../lib/formato'
import CitasFuente from '../components/CitasFuente.vue'
import HorasExtraCalculadora from '../components/HorasExtraCalculadora.vue'

const perfil = usePerfilStore()

onMounted(() => {
  perfil.cargarProvincias()
})

/** Pedagogía D20: "según tu convenio eres nivel III" dicho con normalidad. */
const dimensionesResueltas = computed(() =>
  perfil.ocupacion
    ? Object.entries(perfil.ocupacion.dimensiones).map(
        ([dim, valor]) => `${etiquetaDimension(dim).toLowerCase()} ${valor}`,
      )
    : [],
)

const siguientePendiente = computed(() => perfil.pendientesSinResponder[0] ?? null)

function onProvincia(event: Event) {
  perfil.elegirProvincia((event.target as HTMLSelectElement).value)
}

function onPuesto(event: Event) {
  perfil.elegirPuesto((event.target as HTMLSelectElement).value)
}
</script>

<template>
  <main class="perfil">
    <h1>Tu convenio, en claro</h1>
    <p class="intro">
      Dos preguntas y te decimos lo mínimo que te tienen que pagar. Sin registrarte, sin guardar nada.
    </p>

    <!-- Paso 1: ¿dónde trabajas? -->
    <section class="paso">
      <label
        class="paso-titulo"
        for="provincia"
      >¿En qué provincia trabajas?</label>
      <select
        id="provincia"
        class="selector"
        :value="perfil.provincia ?? ''"
        @change="onProvincia"
      >
        <option
          value=""
          disabled
        >
          Elige tu provincia
        </option>
        <option
          v-for="p in perfil.provincias"
          :key="p"
          :value="p"
        >
          {{ p }}
        </option>
      </select>
    </section>

    <!-- Paso 2: ¿en qué tipo de sitio? -->
    <section
      v-if="perfil.provincia"
      class="paso"
    >
      <p class="paso-titulo">
        ¿En qué tipo de sitio?
      </p>
      <div class="opciones">
        <button
          v-for="s in SUBSECTORES"
          :key="s.clave"
          type="button"
          class="opcion"
          :class="{ activa: perfil.subsector === s.clave }"
          :aria-pressed="perfil.subsector === s.clave"
          @click="perfil.elegirSubsector(s.clave)"
        >
          {{ s.etiqueta }}
        </button>
      </div>
    </section>

    <p
      v-if="perfil.error"
      class="error"
      role="alert"
    >
      {{ perfil.error }}
    </p>

    <!-- Tu convenio -->
    <section
      v-if="perfil.convenio"
      class="tarjeta"
    >
      <p class="tarjeta-etiqueta">
        Tu convenio
      </p>
      <p class="tarjeta-nombre">
        {{ perfil.convenio.nombre }}
      </p>
      <p class="tarjeta-detalle">
        Vigencia: {{ formatearFecha(perfil.convenio.vigenciaDesde) }} –
        {{ formatearFecha(perfil.convenio.vigenciaHasta) }}
      </p>
    </section>

    <!-- Paso 3: ¿de qué trabajas? -->
    <section
      v-if="perfil.convenio"
      class="paso"
    >
      <label
        class="paso-titulo"
        for="puesto"
      >¿De qué trabajas?</label>
      <select
        id="puesto"
        class="selector"
        :value="perfil.puestoId ?? ''"
        @change="onPuesto"
      >
        <option
          value=""
          disabled
        >
          Elige tu puesto
        </option>
        <option
          v-for="p in perfil.puestos"
          :key="p.id"
          :value="p.id"
        >
          {{ p.etiqueta }}
        </option>
      </select>
    </section>

    <!-- Puesto sin mapear todavía -->
    <section
      v-if="perfil.puestoNoMapeado"
      class="tarjeta"
    >
      <p>
        Todavía no tenemos tu puesto cruzado con las tablas de este convenio.
        Estamos en ello.
      </p>
      <button
        type="button"
        class="opcion"
        disabled
      >
        Modo manual (próximamente)
      </button>
    </section>

    <!-- Pedagogía D20: tu clasificación según el convenio -->
    <section
      v-if="perfil.ocupacion && dimensionesResueltas.length > 0"
      class="nota-nivel"
    >
      <p>
        Según tu convenio, tu puesto es <strong>{{ dimensionesResueltas.join(', ') }}</strong>.
        Así es como el convenio clasifica los puestos para asignar el sueldo mínimo.
        <template v-if="perfil.ocupacion.articulo">
          Lo dice el {{ perfil.ocupacion.articulo }}.
        </template>
      </p>
    </section>

    <!-- Pregunta pendiente (p. ej. clase de empresa) -->
    <section
      v-if="siguientePendiente"
      class="paso"
    >
      <p class="paso-titulo">
        Una cosa más: ¿{{ etiquetaDimension(siguientePendiente.dimension).toLowerCase() }}?
      </p>
      <p
        v-if="explicacionDimension(siguientePendiente.dimension)"
        class="ayuda"
      >
        {{ explicacionDimension(siguientePendiente.dimension) }}
      </p>
      <div class="opciones">
        <button
          v-for="valor in siguientePendiente.valores"
          :key="valor"
          type="button"
          class="opcion"
          :aria-pressed="perfil.respuestas[siguientePendiente.dimension] === valor"
          @click="perfil.responderPendiente(siguientePendiente.dimension, valor)"
        >
          {{ valor }}
        </button>
      </div>
    </section>

    <!-- Resultado: tu salario mínimo -->
    <section
      v-if="perfil.salario"
      class="tarjeta resultado"
    >
      <p class="tarjeta-etiqueta">
        Tu salario base mínimo
      </p>
      <p class="importe">
        {{ formatearImporte(perfil.salario.importe) }}
        <span class="unidad">{{ etiquetaUnidad(perfil.salario.unidad) }}</span>
      </p>
      <p
        v-if="perfil.salario.unidad !== 'EUR/mes'"
        class="aviso-unidad"
      >
        Ojo: este convenio publica el salario en esta unidad, no al mes.
      </p>
      <CitasFuente :citas="perfil.salario.citas" />
    </section>

    <p
      v-if="perfil.cargando"
      class="cargando"
      role="status"
      aria-live="polite"
    >
      Cargando...
    </p>

    <!-- Calculadora de horas extra -->
    <section
      v-if="perfil.convenio"
      class="tarjeta"
    >
      <HorasExtraCalculadora
        :convenio-id="perfil.convenio.id"
        :salario-mensual-sugerido="perfil.salarioMensualPrefill"
      />
    </section>
  </main>
</template>

<style scoped>
.perfil {
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

.paso {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.paso-titulo {
  font-weight: 600;
}

.ayuda {
  font-size: 0.85rem;
  opacity: 0.75;
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

.opcion:disabled {
  opacity: 0.55;
  cursor: default;
}

.tarjeta {
  border: 1px solid color-mix(in srgb, var(--color-text) 20%, transparent);
  border-radius: 0.75rem;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.tarjeta-etiqueta {
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  opacity: 0.7;
}

.tarjeta-nombre {
  font-weight: 600;
  font-size: 1.05rem;
}

.tarjeta-detalle {
  font-size: 0.9rem;
  opacity: 0.8;
}

.nota-nivel {
  font-size: 0.95rem;
  border-left: 3px solid var(--color-accent);
  padding-left: 0.75rem;
  opacity: 0.9;
}

.resultado .importe {
  font-size: 2rem;
  font-weight: 700;
}

.resultado .unidad {
  font-size: 1rem;
  font-weight: 400;
  opacity: 0.8;
}

.aviso-unidad {
  font-size: 0.9rem;
  font-weight: 600;
}

.error {
  color: #c0392b;
}

.cargando {
  opacity: 0.7;
  font-size: 0.9rem;
}
</style>
