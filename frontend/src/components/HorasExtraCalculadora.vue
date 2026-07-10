<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { postHorasExtra, type HorasExtra } from '../services/convenios'
import { formatearImporte, mensajeDeError } from '../lib/formato'
import { revelaEscalonado } from '../lib/animacion'
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
const resultadoRef = ref<HTMLElement | null>(null)

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

/* El resultado recién calculado (con su desglose y sus citas) entra
 * escalonado, igual que cualquier bloque de datos que aparece de golpe. */
watch(resultado, async (nuevo) => {
  if (!nuevo) {
    return
  }
  await nextTick()
  const bloques = resultadoRef.value?.querySelectorAll(':scope > *')
  if (bloques) {
    revelaEscalonado(bloques)
  }
})

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
    <h2 class="titulo-seccion">
      ¿Te deben horas extra?
    </h2>
    <p class="texto-sm texto-suave">
      Dinos cuántas horas de más has hecho y calculamos lo que te deben como mínimo.
    </p>

    <div class="campo">
      <label for="horas-extra">Horas extra</label>
      <input
        id="horas-extra"
        v-model.number="horas"
        type="number"
        min="0"
        step="0.5"
        inputmode="decimal"
        placeholder="Ej: 10"
      >
    </div>

    <div class="campo">
      <label for="salario-base-mensual">Tu salario base al mes (bruto, sin pluses)</label>
      <input
        id="salario-base-mensual"
        v-model.number="salarioMensual"
        class="input-salario"
        type="number"
        min="0"
        step="0.01"
        inputmode="decimal"
        placeholder="Ej: 1425.50"
        @input="salarioTocado = true"
      >
      <p
        v-if="salarioMensualSugerido !== null"
        class="campo-ayuda"
      >
        Prellenado con el mínimo de tu convenio. Si cobras más, pon lo tuyo.
      </p>
    </div>

    <div class="campo">
      <label for="pluses-anuales">Pluses al año (opcional)</label>
      <input
        id="pluses-anuales"
        v-model.number="plusesAnuales"
        type="number"
        min="0"
        step="0.01"
        inputmode="decimal"
        placeholder="0"
      >
    </div>

    <button
      class="boton boton--ancho"
      type="button"
      :disabled="calculando"
      @click="calcular"
    >
      {{ calculando ? 'Calculando...' : 'Calcular' }}
    </button>

    <p
      v-if="error"
      class="error aviso-bloque"
      role="alert"
    >
      {{ error }}
    </p>

    <div
      v-if="resultado"
      ref="resultadoRef"
      class="resultado"
    >
      <p class="importe-grande">
        Te deben al menos <strong class="num">{{ formatearImporte(resultado.importe) }} €</strong>
      </p>
      <p class="detalle texto-sm texto-suave">
        Cada hora extra vale como mínimo <span class="num">{{ formatearImporte(resultado.precioHora) }}</span> €.
      </p>
      <details class="desglose">
        <summary>¿De dónde sale este mínimo?</summary>
        <p>
          Tu convenio fija un salario base de
          <strong class="num">{{ formatearImporte(resultado.desglose.salarioBaseMensual) }} € al mes</strong>
          y <strong class="num">{{ resultado.desglose.mensualidades }} pagas</strong> al año<template
            v-if="resultado.desglose.plusesAnuales > 0"
          >
            , más {{ formatearImporte(resultado.desglose.plusesAnuales) }} € de pluses anuales
          </template>.
          <!-- El divisor no siempre es jornada anual: Tenerife no la fija y usa
               un divisor de valor hora propio (Arts. 23 y 24). Etiquetarlo mal
               contradiría la cita de fuente de justo debajo. -->
          Repartido entre las
          <strong class="num">{{ formatearImporte(resultado.desglose.divisorHoras) }} horas</strong>
          {{ resultado.desglose.esDivisorExplicito
            ? 'del divisor de valor hora que fija tu convenio'
            : 'de jornada anual' }}, tu hora ordinaria sale a
          <strong class="num">{{ formatearImporte(resultado.desglose.valorHora) }} €</strong>.
          La ley no permite pagar la hora extra por debajo de tu hora ordinaria
          (art. 35 del Estatuto de los Trabajadores).
        </p>
        <p v-if="resultado.precioHora > resultado.desglose.valorHora">
          Además, tu convenio fija un precio de hora extra mejor:
          <strong class="num">{{ formatearImporte(resultado.precioHora) }} €</strong>. Se aplica el más alto.
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
  gap: var(--esp-md);
}

.importe-grande {
  font-size: var(--tipo-lg);
}

.desglose {
  font-size: var(--tipo-sm);
}

.desglose summary {
  cursor: pointer;
  color: var(--verde);
  font-weight: var(--peso-etiqueta);
}

.desglose p {
  margin: var(--esp-xs) 0 0;
  line-height: 1.5;
}

.resultado {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
  padding-top: var(--esp-xs);
}
</style>
