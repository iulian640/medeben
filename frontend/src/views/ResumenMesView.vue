<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useResumenStore } from '../stores/resumen'
import { formatearHoras, formatearImporte } from '../lib/formato'
import { formatearMinutos } from '../lib/libreta'
import { etiquetaMes } from '../lib/meses'
import CitasFuente from '../components/CitasFuente.vue'

const resumen = useResumenStore()

onMounted(() => {
  resumen.cargar()
})

/** Hay horas extra que cobrar este mes (el caso feliz-triste: te deben algo). */
const hayExtras = computed(
  () => resumen.resumen !== null && resumen.resumen.horasExtra.minutos > 0,
)

/** El progreso hacia el tope anual de horas extra (D22), acotado a 100. */
const porcentajeTope = computed(() => {
  const tope = resumen.resumen?.topeAnual
  if (!tope || tope.horas <= 0) {
    return 0
  }
  return Math.min(100, Math.round((tope.acumuladoAnioHoras / tope.horas) * 100))
})

/**
 * El 422 sobre horario y el de perfil llevan a pantallas distintas. El match
 * va sobre el detail (texto libre) en minúsculas: lo sólido sería un código
 * estable RFC 7807 del backend — anotado como pendiente (review).
 */
const enlaceIncompleto = computed(() => {
  if (!resumen.incompleto) {
    return null
  }
  return resumen.incompleto.toLowerCase().includes('horario')
    ? { a: '/libreta', texto: 'Ir a tu libreta para crear tu horario' }
    : { a: '/cuenta', texto: 'Completar tu perfil' }
})
</script>

<template>
  <main class="resumen">
    <header class="cabecera">
      <h1>Lo tuyo, este mes</h1>
      <RouterLink
        class="enlace-libreta"
        to="/libreta"
      >
        Ir a la libreta
      </RouterLink>
    </header>

    <nav
      class="nav-mes"
      aria-label="Cambiar de mes"
    >
      <button
        type="button"
        class="secundario"
        aria-label="Mes anterior"
        :disabled="resumen.cargando"
        @click="resumen.mesAnterior()"
      >
        ←
      </button>
      <p class="titulo-mes">
        {{ etiquetaMes(resumen.mes) }}
      </p>
      <button
        type="button"
        class="secundario"
        aria-label="Mes siguiente"
        :disabled="resumen.esMesActual || resumen.cargando"
        @click="resumen.mesSiguiente()"
      >
        →
      </button>
    </nav>

    <p
      v-if="resumen.cargando"
      class="cargando"
      role="status"
      aria-live="polite"
    >
      Echando cuentas...
    </p>

    <!-- Falta un dato configurable (422): guía, no error. -->
    <section
      v-else-if="resumen.incompleto"
      class="tarjeta guia"
    >
      <h2 class="guia-titulo">
        Aún no puedo echar las cuentas de este mes
      </h2>
      <p>{{ resumen.incompleto }}</p>
      <RouterLink
        v-if="enlaceIncompleto"
        class="cta"
        :to="enlaceIncompleto.a"
      >
        {{ enlaceIncompleto.texto }}
      </RouterLink>
    </section>

    <p
      v-else-if="resumen.error"
      class="error"
      role="alert"
    >
      {{ resumen.error }}
      <button
        type="button"
        class="secundario"
        @click="resumen.cargar()"
      >
        Reintentar
      </button>
    </p>

    <template v-else-if="resumen.resumen">
      <!-- El número gordo: la razón de ser de la app. -->
      <section
        class="importe-hero"
        :class="{ 'sin-extras': !hayExtras }"
        aria-labelledby="importe-titulo"
      >
        <h2 id="importe-titulo">
          <template v-if="hayExtras">
            Por tus horas extra te deben, como mínimo
          </template>
          <template v-else>
            Horas extra apuntadas este mes
          </template>
        </h2>
        <p
          v-if="hayExtras"
          class="importe"
        >
          {{ formatearImporte(resumen.resumen.importeEstimado.importe) }} €
        </p>
        <p
          v-else
          class="importe"
        >
          0 h
        </p>
        <p
          v-if="hayExtras"
          class="importe-detalle"
        >
          {{ formatearMinutos(resumen.resumen.horasExtra.minutos) }} extra
          a {{ formatearImporte(resumen.resumen.importeEstimado.precioHora) }} € la hora
        </p>
        <p
          v-else
          class="importe-detalle"
        >
          Si un día echas más horas que las de tu horario, aquí verás lo que te deben.
        </p>
      </section>

      <!-- El mes en horas: teórico vs real, sin dramatismo. -->
      <section class="tarjeta">
        <h2 class="tarjeta-etiqueta">
          Tu mes en horas
        </h2>
        <dl class="horas">
          <div class="fila">
            <dt>Según tu horario</dt>
            <dd>{{ formatearMinutos(resumen.resumen.minutosTeoricos) }}</dd>
          </div>
          <div class="fila">
            <dt>Apuntado en tu libreta</dt>
            <dd>{{ formatearMinutos(resumen.resumen.minutosReales) }}</dd>
          </div>
          <div
            v-if="resumen.resumen.deficitInformativo.minutos > 0"
            class="fila"
          >
            <dt>Horas de menos (informativo)</dt>
            <dd>{{ formatearMinutos(resumen.resumen.deficitInformativo.minutos) }}</dd>
          </div>
        </dl>
        <p
          v-if="resumen.resumen.diasSinCalcular > 0"
          class="nota"
        >
          {{ resumen.resumen.diasSinCalcular }}
          {{ resumen.resumen.diasSinCalcular === 1 ? 'día quedó' : 'días quedaron' }}
          sin calcular: no se inventa nada, simplemente no cuentan.
        </p>
      </section>

      <!-- Tope anual de horas extra (D22). -->
      <section
        class="tarjeta"
        aria-labelledby="tope-titulo"
      >
        <h2
          id="tope-titulo"
          class="tarjeta-etiqueta"
        >
          Tu año, contra el tope legal
        </h2>
        <p>
          Llevas <strong>{{ formatearHoras(resumen.resumen.topeAnual.acumuladoAnioHoras) }} h extra</strong>
          de las {{ resumen.resumen.topeAnual.horas }} h que permite la ley al año.
        </p>
        <div
          class="barra-tope"
          role="progressbar"
          :aria-valuenow="porcentajeTope"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <div
            class="barra-tope-relleno"
            :class="{ alerta: porcentajeTope >= 80 }"
            :style="{ width: `${porcentajeTope}%` }"
          />
        </div>
        <CitasFuente :citas="resumen.resumen.topeAnual.citas" />
      </section>

      <div
        v-if="resumen.resumen.avisos.length > 0"
        role="alert"
      >
        <p
          v-for="aviso in resumen.resumen.avisos"
          :key="aviso"
          class="aviso"
        >
          ⚠ {{ aviso }}
        </p>
      </div>

      <!-- D18: no me creas, compruébalo. -->
      <section
        v-if="hayExtras"
        class="tarjeta"
      >
        <h2 class="tarjeta-etiqueta">
          De dónde sale la cifra
        </h2>
        <p class="nota">
          Salario base aplicado:
          {{ formatearImporte(resumen.resumen.importeEstimado.salarioBaseAplicado) }} € al mes
          <template v-if="resumen.resumen.importeEstimado.salarioRealUsado">
            (tu salario declarado, que es mayor que el mínimo del convenio)
          </template>
          <template v-else>
            (el mínimo de tu convenio)
          </template>
        </p>
        <CitasFuente :citas="resumen.resumen.importeEstimado.citas" />
      </section>
    </template>
  </main>
</template>

<style scoped>
.resumen {
  max-width: 480px;
  margin: 0 auto;
  padding: 1.25rem 1rem 3rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.cabecera {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}

h1 {
  font-size: 1.5rem;
}

.enlace-libreta {
  color: var(--color-accent);
  font-size: 0.95rem;
  white-space: nowrap;
}

.nav-mes {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.titulo-mes {
  font-weight: 600;
  text-align: center;
  text-transform: capitalize;
}

#importe-titulo {
  margin: 0;
  font-size: 1rem;
  font-weight: 400;
}

.importe-hero {
  text-align: center;
  padding: 1.75rem 1rem;
  border-radius: 1rem;
  background: color-mix(in srgb, var(--color-accent) 12%, transparent);
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.importe-hero.sin-extras {
  background: color-mix(in srgb, var(--color-text) 6%, transparent);
}

.importe {
  font-size: 3rem;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  color: var(--color-accent);
}

.sin-extras .importe {
  color: var(--color-text);
  opacity: 0.85;
}

.importe-detalle {
  font-size: 0.95rem;
  opacity: 0.85;
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
  margin: 0;
  font-weight: 600;
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  opacity: 0.7;
}

.horas {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin: 0;
}

.fila {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}

.fila dd {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  margin: 0;
}

.barra-tope {
  height: 0.5rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-text) 12%, transparent);
  overflow: hidden;
}

.barra-tope-relleno {
  height: 100%;
  border-radius: 999px;
  background: var(--color-accent);
  transition: width 300ms ease;
}

.barra-tope-relleno.alerta {
  background: #c0392b;
}

.guia .guia-titulo {
  margin: 0;
  font-weight: 600;
  font-size: 1.05rem;
}

.cta {
  display: inline-block;
  font-weight: 600;
  padding: 0.9rem 1rem;
  border-radius: 0.6rem;
  background: var(--color-accent);
  color: var(--color-bg);
  text-align: center;
}

.aviso {
  border-left: 3px solid #c0392b;
  padding-left: 0.75rem;
  font-size: 0.95rem;
}

.secundario {
  font: inherit;
  font-size: 0.9rem;
  padding: 0.5rem 0.75rem;
  border: 1px solid color-mix(in srgb, var(--color-text) 30%, transparent);
  border-radius: 0.6rem;
  background: var(--color-bg);
  color: var(--color-text);
  cursor: pointer;
}

.secundario:disabled {
  opacity: 0.4;
  cursor: default;
}

.nota {
  font-size: 0.9rem;
  opacity: 0.8;
}

.error {
  color: #c0392b;
}

.cargando {
  opacity: 0.7;
  font-size: 0.9rem;
}
</style>
