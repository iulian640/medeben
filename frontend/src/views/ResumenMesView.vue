<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useResumenStore } from '../stores/resumen'
import { getInformeAnio, getInformeMes } from '../services/resumen'
import { formatearHoras, formatearImporte, mensajeDeError } from '../lib/formato'
import { formatearMinutos } from '../lib/libreta'
import { etiquetaMes } from '../lib/meses'
import { revelaEscalonado } from '../lib/animacion'
import CitasFuente from '../components/CitasFuente.vue'
import ImporteDinero from '../components/ImporteDinero.vue'

const resumen = useResumenStore()
const cuerpo = ref<HTMLElement | null>(null)

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
 * La guía del 422 se decide con el CÓDIGO estable del backend (RFC 7807):
 * PERFIL y HORARIO los arregla el usuario; DATOS_CONVENIO no depende de él
 * (mandarle a "completar" un perfil ya completo era un bucle sin salida).
 * Sin código (backend viejo en un despliegue a medias) se adivina sobre el
 * texto, que es mejor que dejar sin salida.
 */
const enlaceIncompleto = computed(() => {
  if (!resumen.incompleto) {
    return null
  }
  switch (resumen.incompletoCodigo) {
    case 'PERFIL':
      return { a: '/cuenta', texto: 'Completar tu perfil' }
    case 'HORARIO':
      return { a: '/horario', texto: 'Crear tu horario' }
    case 'DATOS_CONVENIO':
    case 'CONVENIO_NO_DISPONIBLE':
      return null
    default:
      return resumen.incompleto.toLowerCase().includes('horario')
        ? { a: '/horario', texto: 'Crear tu horario' }
        : { a: '/cuenta', texto: 'Completar tu perfil' }
  }
})

/** El dato que falta es del CONVENIO: se explica que no depende del usuario. */
const faltaDatoDelConvenio = computed(() => resumen.incompletoCodigo === 'DATOS_CONVENIO')

/*
 * Descarga del informe PDF: con el token SOLO en memoria, un <a href> a pelo
 * iría sin sesión y daría 401 — se pide con fetch y se descarga como Blob.
 */
const descargandoInforme = ref(false)
const errorInforme = ref<string | null>(null)

async function descargaPdf(nombre: string, pide: () => Promise<Blob>) {
  if (descargandoInforme.value) {
    return
  }
  descargandoInforme.value = true
  errorInforme.value = null
  try {
    const pdf = await pide()
    const url = URL.createObjectURL(pdf)
    try {
      const enlace = document.createElement('a')
      enlace.href = url
      enlace.download = nombre
      enlace.click()
    } finally {
      URL.revokeObjectURL(url)
    }
  } catch (e) {
    errorInforme.value = mensajeDeError(e)
  } finally {
    descargandoInforme.value = false
  }
}

/* El mes/año se captura UNA sola vez ANTES de pedir: si navegas de mes con
 * la descarga en vuelo, el nombre del fichero no puede desincronizarse del
 * contenido (es evidencia: un julio guardado como junio sería un dato falso). */
function descargaInforme() {
  const mes = resumen.mes
  return descargaPdf(`medeben-informe-${mes}.pdf`, () => getInformeMes(mes))
}

function descargaHistorico() {
  const anio = resumen.mes.slice(0, 4)
  return descargaPdf(`medeben-historico-${anio}.pdf`, () => getInformeAnio(anio))
}

/* Un error del informe pertenece al mes en que ocurrió: al cambiar de mes se retira. */
watch(
  () => resumen.mes,
  () => {
    errorInforme.value = null
  },
)

/* Cuando llegan los datos, las secciones entran escalonadas (la cifra ya
 * trae su propia cuenta). Con movimiento reducido no pasa nada de esto. */
watch(
  () => resumen.resumen,
  async (nuevo) => {
    if (!nuevo) {
      return
    }
    await nextTick()
    const bloques = cuerpo.value?.querySelectorAll(':scope > *')
    if (bloques) {
      revelaEscalonado(bloques)
    }
  },
)
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
        class="boton-secundario paso-mes"
        aria-label="Mes anterior"
        :disabled="resumen.cargando"
        @click="resumen.mesAnterior()"
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        ><path d="M14.5 6 9 12l5.5 6" /></svg>
      </button>
      <p class="titulo-mes">
        {{ etiquetaMes(resumen.mes) }}
      </p>
      <button
        type="button"
        class="boton-secundario paso-mes"
        aria-label="Mes siguiente"
        :disabled="resumen.esMesActual || resumen.cargando"
        @click="resumen.mesSiguiente()"
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        ><path d="m9.5 6 5.5 6-5.5 6" /></svg>
      </button>
    </nav>

    <p
      v-if="resumen.cargando"
      class="cargando texto-suave"
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
      <h2 class="titulo-seccion">
        Aún no puedo echar las cuentas de este mes
      </h2>
      <p>{{ resumen.incompleto }}</p>
      <p
        v-if="faltaDatoDelConvenio"
        class="texto-sm texto-suave"
      >
        Esto no depende de ti: tu perfil está bien. Falta un dato de tu
        convenio (la tabla o la jornada publicadas) y estamos completándolo.
        En cuanto esté, la cifra saldrá sola.
      </p>
      <RouterLink
        v-if="enlaceIncompleto"
        class="boton boton--ancho"
        :to="enlaceIncompleto.a"
      >
        {{ enlaceIncompleto.texto }}
      </RouterLink>
    </section>

    <div
      v-else-if="resumen.error"
      class="error aviso-bloque"
      role="alert"
    >
      <p>{{ resumen.error }}</p>
      <button
        type="button"
        class="boton-secundario"
        @click="resumen.cargar()"
      >
        Reintentar
      </button>
    </div>

    <div
      v-else-if="resumen.resumen"
      ref="cuerpo"
      class="cuerpo"
    >
      <!-- El número gordo: la razón de ser de la app. -->
      <section
        class="hero"
        aria-labelledby="importe-titulo"
      >
        <h2
          id="importe-titulo"
          class="hero-titulo"
        >
          <template v-if="hayExtras">
            Por tus horas extra te deben, como mínimo
          </template>
          <template v-else>
            Horas extra apuntadas este mes
          </template>
        </h2>
        <ImporteDinero
          v-if="hayExtras"
          :importe="resumen.resumen.importeEstimado.importe"
        />
        <p
          v-else
          class="importe importe-cero num"
        >
          0 h
        </p>
        <p
          v-if="hayExtras"
          class="hero-detalle texto-suave num"
        >
          {{ formatearMinutos(resumen.resumen.horasExtra.minutos) }} extra
          a {{ formatearImporte(resumen.resumen.importeEstimado.precioHora) }} € la hora
        </p>
        <p
          v-else
          class="hero-detalle texto-suave"
        >
          Si un día echas más horas que las de tu horario, aquí verás lo que te deben.
        </p>
      </section>

      <!-- El mes en horas: teórico vs real, con puntos de guía de nómina. -->
      <section class="tarjeta">
        <h2 class="titulo-seccion">
          Tu mes en horas
        </h2>
        <dl class="horas">
          <div class="fila">
            <dt>Según tu horario</dt>
            <dd class="num">
              {{ formatearMinutos(resumen.resumen.minutosTeoricos) }}
            </dd>
          </div>
          <div class="fila">
            <dt>Apuntado en tu libreta</dt>
            <dd class="num">
              {{ formatearMinutos(resumen.resumen.minutosReales) }}
            </dd>
          </div>
          <div
            v-if="resumen.resumen.deficitInformativo.minutos > 0"
            class="fila"
          >
            <dt>Horas de menos (informativo)</dt>
            <dd class="num">
              {{ formatearMinutos(resumen.resumen.deficitInformativo.minutos) }}
            </dd>
          </div>
        </dl>
        <p
          v-if="resumen.resumen.diasSinCalcular > 0"
          class="texto-sm texto-suave"
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
          class="titulo-seccion"
        >
          Tu año, contra el tope legal
        </h2>
        <p>
          Llevas <strong class="num">{{ formatearHoras(resumen.resumen.topeAnual.acumuladoAnioHoras) }} h extra</strong>
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
            :style="{ transform: `scaleX(${porcentajeTope / 100})` }"
          />
        </div>
        <CitasFuente :citas="resumen.resumen.topeAnual.citas" />
      </section>

      <div
        v-if="resumen.resumen.avisos.length > 0"
        class="avisos"
        role="alert"
      >
        <p
          v-for="aviso in resumen.resumen.avisos"
          :key="aviso"
          class="aviso aviso-bloque"
        >
          <strong class="aviso-marca">⚠</strong> {{ aviso }}
        </p>
      </div>

      <!-- La evidencia en papel (README): el diario sellado, las cuentas y
           sus fuentes, generado por el backend con el mismo motor. -->
      <div class="informe">
        <button
          type="button"
          class="boton-secundario boton--ancho"
          :disabled="descargandoInforme"
          @click="descargaInforme"
        >
          {{ descargandoInforme ? 'Generando el informe...' : `Descargar el informe de ${etiquetaMes(resumen.mes)} (PDF)` }}
        </button>
        <button
          type="button"
          class="boton-fantasma descarga-anual"
          :disabled="descargandoInforme"
          @click="descargaHistorico"
        >
          Descargar el histórico de {{ resumen.mes.slice(0, 4) }} (PDF)
        </button>
        <p class="texto-xs texto-suave">
          Con tu diario sellado, las cuentas y sus fuentes: para enseñarlo tal
          cual a un sindicato o a un abogado. El histórico del año se genera
          como mucho una vez al día.
        </p>
        <p
          v-if="errorInforme"
          class="aviso-bloque"
          role="alert"
        >
          {{ errorInforme }}
        </p>
      </div>

      <!-- D18: no me creas, compruébalo. -->
      <section
        v-if="hayExtras"
        class="tarjeta"
      >
        <h2 class="titulo-seccion">
          De dónde sale la cifra
        </h2>
        <p class="texto-sm texto-suave">
          Salario base aplicado:
          <span class="num">{{ formatearImporte(resumen.resumen.importeEstimado.salarioBaseAplicado) }} €</span> al mes
          <template v-if="resumen.resumen.importeEstimado.salarioRealUsado">
            (tu salario declarado, que es mayor que el mínimo del convenio)
          </template>
          <template v-else>
            (el mínimo de tu convenio)
          </template>
        </p>
        <CitasFuente :citas="resumen.resumen.importeEstimado.citas" />
      </section>
    </div>
  </main>
</template>

<style scoped>
.resumen {
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

.enlace-libreta {
  font-size: var(--tipo-sm);
  font-weight: var(--peso-etiqueta);
  white-space: nowrap;
}

.nav-mes {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--esp-xs);
}

.paso-mes {
  min-width: 2.75rem;
  padding: 0.5rem;
}

.paso-mes svg {
  width: 1.25rem;
  height: 1.25rem;
}

/* Solo la inicial en mayúscula: capitalize a secas convertía
 * "junio de 2026" en "Junio De 2026". */
.titulo-mes {
  font-weight: var(--peso-etiqueta);
  text-align: center;
}

.titulo-mes::first-letter {
  text-transform: uppercase;
}

.cargando {
  padding: var(--esp-xl) 0;
  text-align: center;
}

.cuerpo {
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
}

/* La cifra vive sobre el papel, sin caja: el espacio es su marco. */
.hero {
  padding: var(--esp-lg) 0 var(--esp-md);
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}

.hero-titulo {
  font-size: var(--tipo-base);
  font-weight: var(--peso-texto);
  color: var(--tinta-suave);
}

.importe-cero {
  font-size: var(--tipo-importe);
  font-weight: var(--peso-importe);
  letter-spacing: -0.02em;
  line-height: 1;
  color: var(--tinta-suave);
}

.hero-detalle {
  font-size: var(--tipo-sm);
}

.horas {
  display: flex;
  flex-direction: column;
  gap: var(--esp-xs);
  margin: 0;
}

/* Las filas con puntos de guía (.fila) son globales: style.css. */

.barra-tope {
  height: 0.625rem;
  border-radius: var(--radio-pastilla);
  background: var(--papel-2);
  border: 1px solid var(--linea);
  overflow: hidden;
}

/* El relleno escala (transform, no width): mismo dibujo, sin relayout. */
.barra-tope-relleno {
  height: 100%;
  border-radius: var(--radio-pastilla);
  background: var(--verde);
  transform-origin: left center;
  transition: transform var(--dur-panel) var(--curva-salida);
}

.barra-tope-relleno.alerta {
  background: var(--alerta);
}

.avisos {
  display: flex;
  flex-direction: column;
  gap: var(--esp-xs);
}

.informe {
  display: flex;
  flex-direction: column;
  gap: var(--esp-xs);
}

.descarga-anual {
  align-self: flex-start;
  padding-inline: 0;
  font-size: var(--tipo-sm);
}

.error {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
  align-items: flex-start;
}
</style>
