<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useFichajesStore } from '../stores/fichajes'
import type { ApuntePeticion, TipoApunte } from '../services/fichajes'
import LibretaOnboarding from '../components/LibretaOnboarding.vue'
import PanelPlegable from '../components/PanelPlegable.vue'
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
const route = useRoute()
const bloqueDia = ref<HTMLElement | null>(null)

/*
 * La fecha en pantalla: /libreta es hoy; /libreta/dia/:fecha (desde "Tu
 * semana") abre ese día para completarlo o corregirlo (D38: 14 días de
 * margen; después, el flujo de rectificación tardía que ya existe). Una
 * fecha inválida en la URL cae a hoy sin romper nada.
 */
const RE_FECHA_ISO = /^\d{4}-\d{2}-\d{2}$/

/** Fecha ISO REAL de calendario: '2026-02-30' pasa la regex pero no existe
 *  (Date la desborda a marzo en vez de fallar), así que se comprueba que el
 *  día reconstruido coincide con el pedido. */
function esFechaRealIso(cruda: string): boolean {
  if (!RE_FECHA_ISO.test(cruda)) {
    return false
  }
  const [anio, mes, dia] = cruda.split('-').map(Number)
  const fecha = new Date(anio, mes - 1, dia)
  return (
    fecha.getFullYear() === anio && fecha.getMonth() + 1 === mes && fecha.getDate() === dia
  )
}

const fechaObjetivo = computed(() => {
  const cruda = route.params.fecha
  return typeof cruda === 'string' && esFechaRealIso(cruda) ? cruda : hoyIso()
})
const esHoy = computed(() => fechaObjetivo.value === hoyIso())
/* El formato ISO ordena igual que el calendario: comparar strings basta. */
const esFuturo = computed(() => fechaObjetivo.value > hoyIso())

/** Primera visita → onboarding (D38). Solo se persiste el flag booleano. */
const mostrarOnboarding = ref(!onboardingVisto())
const mostrarHoraManual = ref(false)
const mostrarAusencia = ref(false)
/** El diario en bruto (la prueba) va plegado: lo que se enseña es la jornada. */
const mostrarApuntes = ref(false)

/** La lectura del día; el `??` tolera un backend anterior sin el campo. */
const tramosDelDia = computed(() => fichajes.dia?.tramos ?? [])
const horaManual = ref('')
const motivo = ref('')

/** La última petición enviada: si el día resulta estar sellado (409), se reenvía confirmada. */
let ultimaPeticion: ApuntePeticion | null = null

/* fechaObjetivo solo elige QUÉ día pedir; a partir de ahí la fecha del día es
 * siempre la que devuelve el backend (fichajes.dia.fecha). En un día pasado
 * la hora manual es el único camino y se abre sola; al navegar a otra fecha
 * los paneles se recogen (el router REUTILIZA el componente entre /libreta y
 * /libreta/dia/:fecha, así que los refs sobreviven a la navegación). */
function cargaFecha() {
  fichajes.cargarDia(fechaObjetivo.value)
  mostrarHoraManual.value = !esHoy.value && !esFuturo.value
  mostrarAusencia.value = false
  mostrarApuntes.value = false
}

onMounted(cargaFecha)
watch(fechaObjetivo, cargaFecha)

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

/*
 * La acción del momento: con la jornada abierta (EN_CURSO) lo primario es
 * salir; en cualquier otro estado, entrar. Los dos botones siguen SIEMPRE
 * disponibles (las jornadas partidas fichan varias entradas y salidas al
 * día), pero solo uno lleva el verde: un único primario por pantalla.
 */
const jornadaAbierta = computed(() => fichajes.dia?.estado === 'EN_CURSO')

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
      <nav
        class="enlaces"
        aria-label="Cambiar de día"
      >
        <RouterLink
          v-if="!esHoy"
          class="enlace-semana"
          to="/libreta"
        >
          Volver a hoy
        </RouterLink>
        <RouterLink
          class="enlace-semana"
          to="/libreta/semana"
        >
          Ver la semana
        </RouterLink>
      </nav>
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
          @click="fichajes.cargarDia(fechaObjetivo)"
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

          <!-- La LECTURA del día: los tramos que el motor deriva del diario,
               con las correcciones ya aplicadas. Una salida de más corrige la
               anterior; aquí se ve el resultado, no la pila de toques. -->
          <dl
            v-if="tramosDelDia.length > 0 || fichajes.dia.entradaAbierta"
            class="jornada"
          >
            <div
              v-for="(tramo, i) in tramosDelDia"
              :key="`${i}-${tramo.entrada}`"
              class="fila"
            >
              <dt>{{ tramosDelDia.length > 1 ? `Turno ${i + 1}` : 'Tu jornada' }}</dt>
              <dd class="num">
                {{ tramo.entrada }} → {{ tramo.salida }}
              </dd>
            </div>
            <!-- Acoplado al estado a propósito: un entradaAbierta rezagado de
                 un backend desincronizado no puede pintar "en curso" en un
                 día que el resto de la tarjeta da por cerrado o ausente. -->
            <div
              v-if="fichajes.dia.estado === 'EN_CURSO' && fichajes.dia.entradaAbierta"
              class="fila"
            >
              <dt>En curso</dt>
              <dd class="num">
                desde las {{ fichajes.dia.entradaAbierta }}
              </dd>
            </div>
          </dl>

          <p
            v-if="fichajes.dia.minutosTrabajados !== null"
            class="minutos"
          >
            Llevas apuntado: {{ formatearMinutos(fichajes.dia.minutosTrabajados) }}.
          </p>
          <!-- El techo de cordura puede dejar el total sin calcular aunque haya
               tramos: se dice, no se esconde (un hueco explicado no confunde). -->
          <p
            v-else-if="tramosDelDia.length > 0"
            class="minutos texto-sm texto-suave"
          >
            Sin total: hay un tramo que no cuadra, y antes que inventar, no se suma.
          </p>

          <!-- El diario en bruto es la prueba: cada toque queda, incluidas las
               correcciones. Se enseña plegado para que la lectura respire. -->
          <template v-if="fichajes.dia.apuntes.length > 0">
            <button
              type="button"
              class="boton-fantasma ver-diario"
              :aria-expanded="mostrarApuntes"
              @click="mostrarApuntes = !mostrarApuntes"
            >
              {{
                mostrarApuntes
                  ? 'Ocultar el diario'
                  : `Ver el diario (${fichajes.dia.apuntes.length} ${fichajes.dia.apuntes.length === 1 ? 'apunte' : 'apuntes'})`
              }}
            </button>
            <PanelPlegable :abierto="mostrarApuntes">
              <ul class="apuntes">
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
            </PanelPlegable>
          </template>

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
          <!-- En un día pasado no existe el "ahora": solo hora manual y
               ausencia, y con las cartas boca arriba sobre lo que vale. Un
               día futuro no se ficha: todavía no ha pasado nada que apuntar. -->
          <p
            v-if="esFuturo"
            class="texto-sm texto-suave"
          >
            Este día aún no ha llegado: no hay nada que fichar todavía.
          </p>
          <p
            v-else-if="!esHoy"
            class="texto-sm texto-suave"
          >
            Estás completando un día pasado: lo que apuntes queda marcado como
            reconstruido, no como fichado al momento.
          </p>
          <template v-if="esHoy">
            <button
              type="button"
              class="boton--ancho"
              :class="jornadaAbierta ? 'boton-secundario' : 'boton'"
              :disabled="fichajes.fichando"
              @click="fichaAhora('ENTRADA')"
            >
              Entro ahora
            </button>
            <button
              type="button"
              class="boton--ancho"
              :class="jornadaAbierta ? 'boton' : 'boton-secundario'"
              :disabled="fichajes.fichando"
              @click="fichaAhora('SALIDA')"
            >
              Salgo ahora
            </button>
          </template>

          <!-- Las excepciones (hora a mano, ausencia) van en su propia fila,
               a media anchura y en fantasma: existen, pero no compiten con el
               gesto diario de "Entro/Salgo ahora". Sus paneles se despliegan
               debajo, a ancho completo. -->
          <template v-if="!esFuturo">
            <div class="excepciones">
              <button
                type="button"
                class="boton-fantasma excepcion"
                :aria-expanded="mostrarHoraManual"
                @click="mostrarHoraManual = !mostrarHoraManual"
              >
                Registrar el turno manualmente
              </button>
              <button
                type="button"
                class="boton-fantasma excepcion"
                :aria-expanded="mostrarAusencia"
                @click="mostrarAusencia = !mostrarAusencia"
              >
                No he ido
              </button>
            </div>

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
          </template>
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

        <!-- Navegación, no acciones: el pie del documento, separado por un
             filete para que no se lea como más botones de fichar. -->
        <nav
          class="pie-enlaces"
          aria-label="Ir a otras pantallas"
        >
          <RouterLink
            class="enlace-resumen"
            to="/resumen"
          >
            Ver cuánto te deben este mes →
          </RouterLink>

          <RouterLink
            class="enlace-resumen"
            to="/horario"
          >
            Tu horario →
          </RouterLink>
        </nav>

        <!-- Solo en la app nativa y en la vista de hoy: recordatorio diario. -->
        <PanelRecordatorio v-if="esHoy" />
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

.enlaces {
  display: flex;
  gap: var(--esp-sm);
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

.jornada {
  display: flex;
  flex-direction: column;
  gap: var(--esp-2xs);
  margin: 0;
}

/* Ligero de aspecto pero con los 44px táctiles intactos (.boton-fantasma). */
.ver-diario {
  align-self: flex-start;
  padding-inline: 0;
  font-size: var(--tipo-sm);
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

/* Las dos excepciones comparten fila a media anchura; si el texto largo no
 * cabe (pantallas estrechas), cada una envuelve en su mitad. */
.excepciones {
  display: flex;
  gap: var(--esp-sm);
}

/* Un panel plegado mide 0 pero sigue sumando el gap de la columna: dos
 * paneles cerrados dejaban un hueco muerto bajo las excepciones. El margen
 * negativo cancela ese gap solo mientras están cerrados. */
.acciones :deep(.plegable:not(.abierto)) {
  margin-top: calc(-1 * var(--esp-sm));
}

.excepcion {
  flex: 1;
  white-space: normal;
}

/* El pie de navegación: filas de índice tras un filete, no más botones. */
.pie-enlaces {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
  border-top: 1px solid var(--linea);
  padding-top: var(--esp-md);
  margin-top: var(--esp-xs);
}

.enlace-resumen {
  font-weight: var(--peso-etiqueta);
}

.enlace-onboarding {
  align-self: flex-start;
}
</style>
