<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { usePerfilStore } from '../stores/perfil'
import { SUBSECTORES } from '../lib/subsectores'
import {
  describeVigencia,
  esUrlSegura,
  etiquetaDimension,
  etiquetaUnidad,
  etiquetaValor,
  explicacionDimension,
  formatearImporte,
  hoyIso,
} from '../lib/formato'
import { revelaEscalonado } from '../lib/animacion'
import CitasFuente from '../components/CitasFuente.vue'
import HorasExtraCalculadora from '../components/HorasExtraCalculadora.vue'
import ImporteDinero from '../components/ImporteDinero.vue'

const perfil = usePerfilStore()
const resultadoRef = ref<HTMLElement | null>(null)

onMounted(() => {
  perfil.cargarProvincias()
})

/** Pedagogía D20: "según tu convenio eres nivel III" dicho con normalidad. */
const dimensionesResueltas = computed(() =>
  perfil.ocupacion
    ? Object.entries(perfil.ocupacion.dimensiones).map(([dim, valor]) => {
        const legible = etiquetaValor(dim, valor)
        // En medio de la frase va en minúscula ("tu puesto es nivel III"),
        // pero sin tocar los romanos ("III") ni las siglas en mayúsculas.
        return /^[A-ZÑÁÉÍÓÚ][a-zñáéíóú]/.test(legible)
          ? legible.charAt(0).toLowerCase() + legible.slice(1)
          : legible
      })
    : [],
)

const siguientePendiente = computed(() => perfil.pendientesSinResponder[0] ?? null)

/*
 * ImporteDinero ya pinta su propio "€" junto a la cifra grande; etiquetaUnidad
 * también lo incluye ("€ al mes") porque antes iba pegado al número sin
 * componente propio. Se recorta el "€ " duplicado — la periodicidad (al mes/
 * la hora/al año) es la misma palabra, solo cambia dónde vive el símbolo.
 */
const periodicidadSalario = computed(() =>
  perfil.salario ? etiquetaUnidad(perfil.salario.unidad).replace(/^€\s*/, '') : '',
)

function onProvincia(event: Event) {
  perfil.elegirProvincia((event.target as HTMLSelectElement).value)
}

function onPuesto(event: Event) {
  perfil.elegirPuesto((event.target as HTMLSelectElement).value)
}

/* El resultado del salario (con sus avisos y sus citas) entra escalonado al
 * llegar: es el momento firma de esta pantalla, igual que en el resumen. */
watch(
  () => perfil.salario,
  async (nuevo) => {
    if (!nuevo) {
      return
    }
    await nextTick()
    const bloques = resultadoRef.value?.querySelectorAll(':scope > *')
    if (bloques) {
      revelaEscalonado(bloques)
    }
  },
)
</script>

<template>
  <main class="perfil">
    <h1>Tu convenio, en claro</h1>
    <p class="intro texto-suave">
      Dos preguntas y te decimos lo mínimo que te tienen que pagar. Sin registrarte, sin guardar nada.
    </p>

    <!-- Paso 1: ¿dónde trabajas? -->
    <div class="campo">
      <label for="provincia">¿En qué provincia trabajas?</label>
      <select
        id="provincia"
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
    </div>

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
          class="opcion boton-secundario"
          :class="{ 'opcion--activa': perfil.subsector === s.clave }"
          :disabled="perfil.cargando"
          :aria-pressed="perfil.subsector === s.clave"
          @click="perfil.elegirSubsector(s.clave)"
        >
          {{ s.etiqueta }}
        </button>
      </div>
    </section>

    <p
      v-if="perfil.error"
      class="error aviso-bloque"
      role="alert"
    >
      {{ perfil.error }}
    </p>

    <!-- Tu convenio -->
    <section
      v-if="perfil.convenio"
      class="tarjeta"
    >
      <h2 class="titulo-seccion">
        Tu convenio
      </h2>
      <p class="tarjeta-nombre">
        {{ perfil.convenio.nombre }}
      </p>
      <p class="tarjeta-detalle texto-sm texto-suave">
        {{ describeVigencia(perfil.convenio.vigenciaDesde, perfil.convenio.vigenciaHasta, hoyIso()) }}
      </p>
      <a
        v-if="esUrlSegura(perfil.convenio.fuenteUrl)"
        class="tarjeta-enlace"
        :href="perfil.convenio.fuenteUrl"
        target="_blank"
        rel="noopener noreferrer"
      >Ver el convenio completo (boletín oficial)</a>
    </section>

    <!-- Paso 3: ¿de qué trabajas? -->
    <div
      v-if="perfil.convenio"
      class="campo"
    >
      <label for="puesto">¿De qué trabajas?</label>
      <select
        id="puesto"
        :value="perfil.puestoId ?? ''"
        :disabled="perfil.cargando"
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
    </div>

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
        class="opcion boton-secundario"
        disabled
      >
        Modo manual (próximamente)
      </button>
    </section>

    <!-- Pedagogía D20: tu clasificación según el convenio -->
    <p
      v-if="perfil.ocupacion && dimensionesResueltas.length > 0"
      class="nota-nivel texto-sm texto-suave"
    >
      Según tu convenio, tu puesto es <strong>{{ dimensionesResueltas.join(', ') }}</strong>.
      Así es como el convenio clasifica los puestos para asignar el sueldo mínimo.
      <template v-if="perfil.ocupacion.articulo">
        Lo dice el {{ perfil.ocupacion.articulo }}.
      </template>
    </p>

    <!-- Pregunta pendiente (p. ej. clase de empresa) -->
    <section
      v-if="siguientePendiente"
      class="paso"
    >
      <p class="paso-titulo">
        Una cosa más y ya lo tienes: ¿{{ etiquetaDimension(siguientePendiente.dimension).toLowerCase() }}?
      </p>
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
          :disabled="perfil.cargando"
          :aria-pressed="perfil.respuestas[siguientePendiente.dimension] === valor"
          @click="perfil.responderPendiente(siguientePendiente.dimension, valor)"
        >
          {{ etiquetaValor(siguientePendiente.dimension, valor) }}
        </button>
      </div>
    </section>

    <!-- Resultado: tu salario mínimo. LA cifra de esta pantalla. -->
    <section
      v-if="perfil.salario"
      ref="resultadoRef"
      class="tarjeta resultado"
    >
      <h2 class="titulo-seccion">
        Tu salario base mínimo
      </h2>
      <ImporteDinero :importe="perfil.salario.importe" />
      <p class="texto-suave">
        {{ periodicidadSalario }}
      </p>
      <p
        v-if="perfil.salario.unidad !== 'EUR/mes'"
        class="aviso-unidad texto-sm"
      >
        Ojo: este convenio publica el salario en esta unidad, no al mes.
      </p>
      <p
        v-if="perfil.salario.bajoSmi && perfil.salario.smiMensual"
        class="aviso-smi aviso-bloque"
        role="alert"
      >
        La tabla de tu convenio para este puesto ha quedado por debajo del
        salario mínimo ({{ formatearImporte(perfil.salario.smiMensual) }} € al mes).
        Por ley no pueden pagarte menos: al año te corresponde al menos el mínimo,
        y si con tus pluses no llega, la diferencia es tuya.
      </p>
      <CitasFuente :citas="perfil.salario.citas" />
    </section>

    <p
      v-if="perfil.cargando"
      class="cargando texto-sm texto-suave"
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

/* Cada pregunta es su propio bloque, apretado por dentro; el ritmo entre
 * pasos lo da el gap generoso de .perfil, no un espaciado local aquí. */
.paso {
  display: flex;
  flex-direction: column;
  gap: var(--esp-xs);
}

.paso-titulo {
  font-size: var(--tipo-sm);
  font-weight: var(--peso-etiqueta);
}

.opciones {
  display: flex;
  flex-direction: column;
  gap: var(--esp-xs);
}

/* Chips de elección: el mismo control que en CuentaView — .boton-secundario
 * compuesto, en lista y alineado a la izquierda. Aquí solo vive el layout. */
.opcion {
  justify-content: flex-start;
  text-align: left;
}

/* Seleccionado = el mismo verde-suave que usa toda la app para marcar una
 * elección (el verde nunca es decoración; aquí es selección). */
.opcion--activa {
  border-color: var(--verde);
  background: var(--verde-suave);
  color: var(--tinta);
}

.tarjeta-nombre {
  font-size: var(--tipo-lg);
  font-weight: var(--peso-titulo);
}

.tarjeta-enlace {
  font-weight: var(--peso-etiqueta);
  align-self: flex-start;
}

.aviso-unidad {
  font-weight: var(--peso-etiqueta);
}
</style>
