<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useFichajesStore } from '../stores/fichajes'
import type { ApuntePeticion, TipoApunte } from '../services/fichajes'
import LibretaOnboarding from '../components/LibretaOnboarding.vue'
import PanelHoraManual from '../components/PanelHoraManual.vue'
import PanelAusencia from '../components/PanelAusencia.vue'
import PanelRectificacionSellado from '../components/PanelRectificacionSellado.vue'
import PanelRecordatorio from '../components/PanelRecordatorio.vue'
import { formatearFecha, hoyIso } from '../lib/formato'
import { pulsoExito } from '../lib/animacion'
import {
  ETIQUETAS_ESTADO,
  ETIQUETAS_ORIGEN,
  ETIQUETAS_TIPO,
  diaSemanaDe,
  formatearMinutos,
  horaActual,
  horaLocalDe,
  marcaOnboardingVisto,
  onboardingVisto,
} from '../lib/libreta'

const fichajes = useFichajesStore()
const bloqueDia = ref<HTMLElement | null>(null)

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
    // El fichaje se confirma con un pulso sobre el bloque del día, no con
    // color: el verde queda solo para el dinero y la acción (Nómina clara).
    await nextTick()
    if (bloqueDia.value) {
      pulsoExito(bloqueDia.value)
    }
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
        class="cargando texto-suave"
        role="status"
        aria-live="polite"
      >
        Cargando tu día...
      </p>

      <div
        v-else-if="!fichajes.dia && fichajes.error"
        class="aviso-bloque"
        role="alert"
      >
        <p>{{ fichajes.error }}</p>
        <button
          type="button"
          class="boton-secundario"
          @click="fichajes.cargarDia(hoyIso())"
        >
          Reintentar
        </button>
      </div>

      <template v-else-if="fichajes.dia">
        <section
          ref="bloqueDia"
          class="tarjeta"
          aria-labelledby="dia-titulo"
        >
          <h2
            id="dia-titulo"
            class="titulo-seccion"
          >
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
              <span class="texto-suave texto-sm"> ({{ ETIQUETAS_ORIGEN[a.origen] }})</span>
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
            ✓ apuntado a las {{ horaLocalDe(fichajes.ultimoSello.registradoEn) }}
          </p>
        </section>

        <!-- Lo diario se hace con un pulgar: las dos acciones grandes van
             arriba, sin competir por espacio con nada más (D "un pulgar y
             cinco segundos"). -->
        <section
          class="acciones"
          aria-label="Fichar"
        >
          <button
            type="button"
            class="boton boton--ancho"
            :disabled="fichajes.fichando"
            @click="fichaAhora('ENTRADA')"
          >
            Entro ahora
          </button>
          <button
            type="button"
            class="boton boton--ancho"
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
          class="aviso-bloque"
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
        class="boton-fantasma enlace-onboarding"
        @click="mostrarOnboarding = true"
      >
        ¿Cómo funciona la libreta?
      </button>
    </template>
  </main>
</template>

<style scoped>
.libreta {
  max-width: 30rem;
  margin: 0 auto;
  padding: var(--esp-lg) var(--esp-md) var(--esp-2xl);
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
}

.cabecera {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--esp-md);
}

h1 {
  font-size: var(--tipo-titulo);
}

.enlace-semana {
  font-size: var(--tipo-sm);
  font-weight: var(--peso-etiqueta);
  white-space: nowrap;
}

.cargando {
  padding: var(--esp-xl) 0;
  text-align: center;
}

.estado {
  font-weight: var(--peso-etiqueta);
}

.apuntes {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--esp-xs);
}

.motivo {
  overflow-wrap: anywhere;
}

.sello {
  font-weight: var(--peso-etiqueta);
}

.acciones {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}

.enlace-resumen {
  font-weight: var(--peso-etiqueta);
}

.enlace-onboarding {
  align-self: flex-start;
}
</style>
