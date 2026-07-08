<script setup lang="ts">
import { ref, watch } from 'vue'
import { postHorasExtra, type HorasExtra } from '../services/convenios'
import { formatearImporte, mensajeDeError } from '../lib/formato'
import CitasFuente from './CitasFuente.vue'

const props = defineProps<{
  convenioId: string
  /** Prellenado con el mínimo del convenio cuando la unidad es EUR/mes. */
  salarioMensualSugerido: number | null
}>()

const horas = ref<number | null>(null)
const salarioMensual = ref<number | null>(props.salarioMensualSugerido)
// Flag explícito: en cuanto el usuario edita el campo, deja de sincronizarse
// con la sugerencia (null-ness no vale de proxy: tras el primer prellenado
// nunca volvería a actualizarse y se quedaría el mínimo viejo).
const salarioTocado = ref(false)
const plusesAnuales = ref<number | null>(null)
const resultado = ref<HorasExtra | null>(null)
const error = ref<string | null>(null)
const calculando = ref(false)

// Mientras el usuario no haya tocado el campo, seguimos la sugerencia del
// convenio (incluido volver a vacío si el nuevo puesto no publica EUR/mes).
watch(
  () => props.salarioMensualSugerido,
  (nuevo) => {
    if (!salarioTocado.value) {
      salarioMensual.value = nuevo
    }
  },
)

async function calcular() {
  if (horas.value === null || horas.value < 0 || salarioMensual.value === null || salarioMensual.value <= 0) {
    error.value = 'Rellena las horas y tu salario base mensual.'
    return
  }
  try {
    calculando.value = true
    error.value = null
    resultado.value = await postHorasExtra({
      convenioId: props.convenioId,
      anio: new Date().getFullYear(),
      salarioBaseMensual: salarioMensual.value,
      plusesAnuales: plusesAnuales.value ?? 0,
      horas: horas.value,
    })
  } catch (e) {
    resultado.value = null
    error.value = mensajeDeError(e)
  } finally {
    calculando.value = false
  }
}
</script>

<template>
  <section class="calculadora">
    <h2>¿Te deben horas extra?</h2>
    <p class="ayuda">
      Dinos cuántas horas de más has hecho y calculamos lo que te deben como mínimo.
    </p>

    <label class="campo">
      <span>Horas extra</span>
      <input
        v-model.number="horas"
        type="number"
        min="0"
        step="0.5"
        inputmode="decimal"
        placeholder="Ej: 10"
      >
    </label>

    <label class="campo">
      <span>Tu salario base al mes (bruto, sin pluses)</span>
      <input
        v-model.number="salarioMensual"
        class="input-salario"
        type="number"
        min="0"
        step="0.01"
        inputmode="decimal"
        placeholder="Ej: 1425.50"
        @input="salarioTocado = true"
      >
      <small v-if="salarioMensualSugerido !== null">
        Prellenado con el mínimo de tu convenio. Si cobras más, pon lo tuyo.
      </small>
    </label>

    <label class="campo">
      <span>Pluses al año (opcional)</span>
      <input
        v-model.number="plusesAnuales"
        type="number"
        min="0"
        step="0.01"
        inputmode="decimal"
        placeholder="0"
      >
    </label>

    <button
      class="boton"
      type="button"
      :disabled="calculando"
      @click="calcular"
    >
      {{ calculando ? 'Calculando...' : 'Calcular' }}
    </button>

    <p
      v-if="error"
      class="error"
      role="alert"
    >
      {{ error }}
    </p>

    <div
      v-if="resultado"
      class="resultado"
    >
      <p class="importe-grande">
        Te deben al menos <strong>{{ formatearImporte(resultado.importe) }} €</strong>
      </p>
      <p class="detalle">
        Cada hora extra vale como mínimo {{ formatearImporte(resultado.precioHora) }} €.
      </p>
      <details class="desglose">
        <summary>¿De dónde sale este mínimo?</summary>
        <p>
          Tu convenio fija un salario base de
          <strong>{{ formatearImporte(resultado.desglose.salarioBaseMensual) }} € al mes</strong>
          y <strong>{{ resultado.desglose.mensualidades }} pagas</strong> al año<template
            v-if="resultado.desglose.plusesAnuales > 0"
          >
            , más {{ formatearImporte(resultado.desglose.plusesAnuales) }} € de pluses anuales
          </template>.
          Repartido entre las
          <strong>{{ formatearImporte(resultado.desglose.jornadaAnualHoras) }} horas</strong>
          de jornada anual, tu hora ordinaria sale a
          <strong>{{ formatearImporte(resultado.desglose.valorHora) }} €</strong>.
          La ley no permite pagar la hora extra por debajo de tu hora ordinaria
          (art. 35 del Estatuto de los Trabajadores).
        </p>
        <p v-if="resultado.precioHora > resultado.desglose.valorHora">
          Además, tu convenio fija un precio de hora extra mejor:
          <strong>{{ formatearImporte(resultado.precioHora) }} €</strong>. Se aplica el más alto.
        </p>
      </details>
      <CitasFuente :citas="resultado.citas" />
    </div>
  </section>
</template>

<style scoped>
.calculadora {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

h2 {
  margin: 0;
  font-size: 1.2rem;
}

.ayuda {
  font-size: 0.9rem;
  opacity: 0.8;
}

.campo {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
  font-weight: 600;
}

.campo input {
  font: inherit;
  font-weight: 400;
  padding: 0.75rem;
  border: 1px solid color-mix(in srgb, var(--color-text) 30%, transparent);
  border-radius: 0.5rem;
  background: var(--color-bg);
  color: var(--color-text);
}

.campo small {
  font-weight: 400;
  opacity: 0.7;
}

.boton {
  font: inherit;
  font-weight: 600;
  padding: 0.9rem 1rem;
  border: none;
  border-radius: 0.6rem;
  background: var(--color-accent);
  color: var(--color-bg);
  cursor: pointer;
}

.boton:disabled {
  opacity: 0.6;
  cursor: default;
}

.error {
  color: #c0392b;
  font-size: 0.9rem;
}

.desglose {
  margin: 0.75rem 0;
  font-size: 0.95rem;
}
.desglose summary {
  cursor: pointer;
  color: var(--color-primario, #1a5fb4);
  font-weight: 600;
}
.desglose p {
  margin: 0.5rem 0 0;
  line-height: 1.5;
}

.resultado {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding-top: 0.5rem;
}

.importe-grande {
  font-size: 1.35rem;
}

.detalle {
  font-size: 0.95rem;
  opacity: 0.85;
}
</style>
