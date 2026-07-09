<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useFichajesStore } from '../stores/fichajes'
import type { ApuntePeticion, TipoApunte } from '../services/fichajes'
import LibretaOnboarding from '../components/LibretaOnboarding.vue'
import PanelHoraManual from '../components/PanelHoraManual.vue'
import PanelAusencia from '../components/PanelAusencia.vue'
import PanelRectificacionSellado from '../components/PanelRectificacionSellado.vue'
import PanelRecordatorio from '../components/PanelRecordatorio.vue'
import { formatearFecha, hoyIso } from '../lib/formato'
import {
  ETIQUETAS_ESTADO,
  ETIQUETAS_ORIGEN,
  ETIQUETAS_TIPO,
  cuentaAtrasSello,
  diaSemanaDe,
  formatearMinutos,
  horaActual,
  horaLocalDe,
  marcaOnboardingVisto,
  onboardingVisto,
} from '../lib/libreta'

const fichajes = useFichajesStore()

/** Primera visita → onboarding (D38). Solo se persiste el flag booleano. */
const mostrarOnboarding = ref(!onboardingVisto())
const mostrarHoraManual = ref(false)
const mostrarAusencia = ref(false)
const horaManual = ref('')
const motivo = ref('')

/** La última petición enviada: si el día resulta estar sellado (409), se reenvía confirmada. */
let ultimaPeticion: ApuntePeticion | null = null

onMounted(() => {
  // hoyIso() solo elige QUÉ día pedir; a partir de ahí la fecha del día es
  // siempre la que devuelve el backend (fichajes.dia.fecha).
  fichajes.cargarDia(hoyIso())
})

function cerrarOnboarding() {
  marcaOnboardingVisto()
  mostrarOnboarding.value = false
}

async function envia(peticion: ApuntePeticion) {
  ultimaPeticion = peticion
  const apuntado = await fichajes.fichar(peticion)
  if (apuntado) {
    mostrarHoraManual.value = false
    mostrarAusencia.value = false
    horaManual.value = ''
    motivo.value = ''
    ultimaPeticion = null
  }
}

function fichaAhora(tipo: TipoApunte) {
  if (!fichajes.dia) {
    return
  }
  envia({
    fecha: fichajes.dia.fecha,
    tipo,
    hora: horaActual(),
    motivo: null,
    rectificacionTardiaConfirmada: false,
  })
}

function fichaManual(tipo: TipoApunte) {
  if (!fichajes.dia || horaManual.value === '') {
    return
  }
  envia({
    fecha: fichajes.dia.fecha,
    tipo,
    hora: horaManual.value,
    motivo: null,
    rectificacionTardiaConfirmada: false,
  })
}

function registraAusencia() {
  if (!fichajes.dia) {
    return
  }
  const texto = motivo.value.trim()
  envia({
    fecha: fichajes.dia.fecha,
    tipo: 'AUSENCIA',
    hora: null,
    motivo: texto === '' ? null : texto,
    rectificacionTardiaConfirmada: false,
  })
}

function reenviaConfirmada(confirmado: boolean) {
  // Comprobación redundante a propósito: una petición con valor probatorio no
  // se marca como confirmada solo porque el botón del hijo estuviera activo.
  if (!ultimaPeticion || !confirmado) {
    return
  }
  envia({ ...ultimaPeticion, rectificacionTardiaConfirmada: true })
}
</script>

<template>
  <main class="libreta">
    <header class="cabecera">
      <h1>Tu libreta</h1>
      <RouterLink
        class="enlace-semana"
        to="/libreta/semana"
      >
        Ver la semana
      </RouterLink>
    </header>

    <LibretaOnboarding
      v-if="mostrarOnboarding"
      @cerrar="cerrarOnboarding"
    />

    <template v-else>
      <p
        v-if="fichajes.cargando"
        class="cargando"
        role="status"
        aria-live="polite"
      >
        Cargando tu día...
      </p>

      <p
        v-else-if="!fichajes.dia && fichajes.error"
        class="error"
        role="alert"
      >
        {{ fichajes.error }}
        <button
          type="button"
          class="secundario"
          @click="fichajes.cargarDia(hoyIso())"
        >
          Reintentar
        </button>
      </p>

      <template v-else-if="fichajes.dia">
        <section
          class="dia"
          aria-labelledby="dia-titulo"
        >
          <h2 id="dia-titulo">
            {{ diaSemanaDe(fichajes.dia.fecha) }} {{ formatearFecha(fichajes.dia.fecha) }}
          </h2>
          <p class="estado">
            {{ ETIQUETAS_ESTADO[fichajes.dia.estado] }}
          </p>

          <ul
            v-if="fichajes.dia.apuntes.length > 0"
            class="apuntes"
          >
            <!-- registradoEn es el sello del servidor: único por apunte y estable. -->
            <li
              v-for="a in fichajes.dia.apuntes"
              :key="a.registradoEn"
            >
              <strong>{{ ETIQUETAS_TIPO[a.tipo] }}</strong>
              <template v-if="a.hora">
                a las {{ a.hora }}
              </template>
              <span class="origen">({{ ETIQUETAS_ORIGEN[a.origen] }})</span>
              <!-- El motivo SIEMPRE interpolado como texto, nunca v-html (RGPD, D38). -->
              <span
                v-if="a.motivo"
                class="motivo"
              >— {{ a.motivo }}</span>
            </li>
          </ul>

          <p
            v-if="fichajes.dia.minutosTrabajados !== null"
            class="minutos"
          >
            Llevas apuntado: {{ formatearMinutos(fichajes.dia.minutosTrabajados) }}.
          </p>

          <p
            v-if="fichajes.ultimoSello"
            class="sello"
            role="status"
          >
            ✓ sellado a las {{ horaLocalDe(fichajes.ultimoSello.registradoEn) }}
          </p>

          <p class="contador">
            <template v-if="fichajes.dia.sellado">
              Este día está sellado desde el {{ formatearFecha(fichajes.dia.selladoDesde) }}.
            </template>
            <template v-else>
              <!-- Contador de cierre (D38): la cuenta atrás dice si aún llegas a corregir. -->
              Este día se sella el {{ diaSemanaDe(fichajes.dia.selladoDesde) }}
              {{ formatearFecha(fichajes.dia.selladoDesde) }} —
              {{ cuentaAtrasSello(hoyIso(), fichajes.dia.selladoDesde) }}.
            </template>
          </p>
        </section>

        <section
          class="acciones"
          aria-label="Fichar"
        >
          <button
            type="button"
            class="principal"
            :disabled="fichajes.fichando"
            @click="fichaAhora('ENTRADA')"
          >
            Entro ahora
          </button>
          <button
            type="button"
            class="principal"
            :disabled="fichajes.fichando"
            @click="fichaAhora('SALIDA')"
          >
            Salgo ahora
          </button>

          <PanelHoraManual
            v-model:abierto="mostrarHoraManual"
            v-model:hora="horaManual"
            :fichando="fichajes.fichando"
            @fichar="fichaManual"
          />

          <PanelAusencia
            v-model:abierto="mostrarAusencia"
            v-model:motivo="motivo"
            :fichando="fichajes.fichando"
            @registrar="registraAusencia"
          />
        </section>

        <p
          v-if="fichajes.error && !fichajes.conflictoSellado"
          class="error"
          role="alert"
        >
          {{ fichajes.error }}
        </p>

        <PanelRectificacionSellado
          v-if="fichajes.conflictoSellado"
          :fichando="fichajes.fichando"
          @confirmar="reenviaConfirmada"
        />

        <RouterLink
          class="enlace-resumen"
          to="/resumen"
        >
          Ver cuánto te deben este mes →
        </RouterLink>

        <!-- Solo en la app nativa: recordatorio diario de fichar. -->
        <PanelRecordatorio />
      </template>

      <button
        type="button"
        class="ver-onboarding"
        @click="mostrarOnboarding = true"
      >
        ¿Cómo funciona la libreta?
      </button>
    </template>
  </main>
</template>

<style scoped>
.libreta {
  max-width: 480px;
  margin: 0 auto;
  padding: 1.25rem 1rem 3rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
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

.enlace-semana {
  color: var(--color-accent);
  font-size: 0.95rem;
  white-space: nowrap;
}

.dia {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.dia h2 {
  font-size: 1.1rem;
}

.estado {
  font-weight: 600;
}

.apuntes {
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.origen {
  opacity: 0.7;
  font-size: 0.9rem;
  margin-left: 0.35rem;
}

.motivo {
  overflow-wrap: anywhere;
}

.sello {
  font-weight: 600;
  color: var(--color-accent);
}

.contador {
  font-size: 0.9rem;
  opacity: 0.85;
}

.acciones {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.principal {
  font: inherit;
  font-size: 1.1rem;
  font-weight: 600;
  padding: 1.1rem 1.5rem;
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

.secundario {
  font: inherit;
  padding: 0.75rem 1rem;
  border: 1px solid color-mix(in srgb, var(--color-text) 30%, transparent);
  border-radius: 0.6rem;
  background: var(--color-bg);
  color: var(--color-text);
  cursor: pointer;
}

.error {
  color: #c0392b;
}

.cargando {
  opacity: 0.7;
  font-size: 0.9rem;
}

.enlace-resumen {
  color: var(--color-accent);
  font-weight: 600;
}

.ver-onboarding {
  font: inherit;
  font-size: 0.9rem;
  background: none;
  border: none;
  color: var(--color-accent);
  cursor: pointer;
  text-decoration: underline;
  align-self: flex-start;
  padding: 0;
}
</style>
