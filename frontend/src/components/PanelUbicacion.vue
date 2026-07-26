<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import { avisoPermisoCaducado, capturaPosicion, pideUbicacion } from '../lib/ubicacion'
import {
  avisoConsentimientoCaducado,
  deleteConsentimientoUbicacion,
  deleteUbicaciones,
  getCentroTrabajo,
  limpiaAvisoConsentimientoCaducado,
  marcaUbicacionActivada,
  marcaUbicacionDesactivada,
  postConsentimientoUbicacion,
  putCentroTrabajo,
  ubicacionActivada,
} from '../services/ubicacion'
import { formatearFecha, mensajeDeError } from '../lib/formato'

/**
 * "Anotar dónde fichas" (Ajustes), SOLO en la app nativa — mismo patrón que
 * PanelRecordatorio.vue: en el navegador no existe.
 */
const esNativo = Capacitor.isNativePlatform()

/** Estado de alto nivel: activa/o no. El detalle del alta vive en abriendoActivacion. */
const activo = ref(ubicacionActivada())

// --- Paso 1: pantalla explicativa + consentimiento (ANTES de cualquier diálogo del sistema) ---
const abriendoActivacion = ref(false)
const aceptoTexto = ref(false)
const enviandoConsentimiento = ref(false)
const errorConsentimiento = ref<string | null>(null)

// --- Paso 2: permiso de Android (pedido aquí, nunca al abrir la app) ---
const pidiendoPermiso = ref(false)
const permisoConcedido = ref(false)
const permisoDenegadoPermanente = ref(false)

// --- Paso 3: marcar el centro estando allí ---
const marcandoCentro = ref(false)
const errorCentro = ref<string | null>(null)
/** precisionMetros solo se conoce justo al marcar el centro (GET no la devuelve: D3, minimización). */
const centroDeclarado = ref<{ declaradoEn: string; precisionMetros: number | null } | null>(null)

/*
 * Con la feature ya activa desde una sesión anterior, se repuebla la fecha
 * de declaración del centro (sin ella, "Declarado el X" solo se vería justo
 * después de marcarlo, y desaparecería al reabrir Ajustes más tarde).
 */
onMounted(async () => {
  if (!activo.value) {
    return
  }
  try {
    const centro = await getCentroTrabajo()
    centroDeclarado.value = { declaradoEn: centro.declaradoEn, precisionMetros: null }
  } catch {
    // Sin centro vigente (404) o caída puntual: no es un error que mostrar
    // aquí, simplemente no se rellena la fecha.
  }
})

// --- Ya activa: borrar histórico ---
const borrandoHistorico = ref(false)
const historicoBorrado = ref(false)

// --- Desactivar ---
const desactivando = ref(false)

// --- Aviso de que el servidor apagó la feature sola (403 en el POST de adjuntar) ---
const avisoConsentimiento = ref(avisoConsentimientoCaducado())

/** Fichajes seguidos sin permiso efectivo: nunca se avisa al fichar, solo aquí. */
const mostrarAvisoPermiso = computed(() => activo.value && avisoPermisoCaducado())

function abrirActivacion() {
  abriendoActivacion.value = true
  aceptoTexto.value = false
  errorConsentimiento.value = null
  permisoConcedido.value = false
  permisoDenegadoPermanente.value = false
  centroDeclarado.value = null
}

function cancelarActivacion() {
  abriendoActivacion.value = false
}

async function confirmarConsentimiento() {
  if (!aceptoTexto.value || enviandoConsentimiento.value) {
    return
  }
  enviandoConsentimiento.value = true
  errorConsentimiento.value = null
  try {
    await postConsentimientoUbicacion()
  } catch (e) {
    // Sin consentimiento acreditado en servidor no hay tratamiento: la
    // feature no se activa, y aquí se queda hasta que lo intente de nuevo.
    errorConsentimiento.value = mensajeDeError(e)
    return
  } finally {
    enviandoConsentimiento.value = false
  }

  // El permiso de Android se pide AQUÍ, justo después del consentimiento, y
  // nunca al abrir la app.
  pidiendoPermiso.value = true
  const estado = await pideUbicacion()
  pidiendoPermiso.value = false
  if (estado === 'concedido') {
    permisoConcedido.value = true
    return
  }
  // Denegado (una vez o para siempre): se queda apagado, un mensaje, y no se
  // vuelve a insistir jamás — el usuario tendrá que volver a abrir esta
  // pantalla por su cuenta si cambia de opinión.
  permisoDenegadoPermanente.value = estado === 'denegado_permanente'
}

async function marcaElCentro() {
  marcandoCentro.value = true
  errorCentro.value = null
  const posicion = await capturaPosicion()
  if (!posicion) {
    marcandoCentro.value = false
    errorCentro.value =
      'No hemos conseguido tu posición ahora mismo. Prueba otra vez estando en el trabajo.'
    return
  }
  try {
    const centro = await putCentroTrabajo(posicion)
    centroDeclarado.value = { declaradoEn: centro.declaradoEn, precisionMetros: posicion.precisionMetros }
    marcaUbicacionActivada()
    activo.value = true
    abriendoActivacion.value = false
  } catch (e) {
    errorCentro.value = mensajeDeError(e)
  } finally {
    marcandoCentro.value = false
  }
}

async function borrarHistorico() {
  if (borrandoHistorico.value) {
    return
  }
  borrandoHistorico.value = true
  historicoBorrado.value = false
  try {
    await deleteUbicaciones()
    historicoBorrado.value = true
  } finally {
    borrandoHistorico.value = false
  }
}

/** El toggle, ya activo: apagarlo revoca (no borra el histórico) y desactiva en local. */
async function alternar(event: Event) {
  const marcado = (event.target as HTMLInputElement).checked
  if (marcado) {
    abrirActivacion()
    return
  }
  desactivando.value = true
  try {
    await deleteConsentimientoUbicacion()
  } finally {
    marcaUbicacionDesactivada()
    activo.value = false
    desactivando.value = false
  }
}

function cierraAvisoConsentimiento() {
  limpiaAvisoConsentimientoCaducado()
  avisoConsentimiento.value = false
}
</script>

<template>
  <section
    v-if="esNativo"
    class="ubicacion tarjeta"
    aria-labelledby="ubicacion-titulo"
  >
    <p
      v-if="avisoConsentimiento"
      class="aviso-bloque"
      role="alert"
    >
      Se ha desactivado "Anotar dónde fichas": tu consentimiento ya no está
      vigente en el servidor.
      <button
        type="button"
        class="boton-fantasma"
        @click="cierraAvisoConsentimiento"
      >
        Entendido
      </button>
    </p>

    <template v-if="!abriendoActivacion">
      <label
        id="ubicacion-titulo"
        class="fila-activar"
      >
        <input
          type="checkbox"
          :checked="activo"
          :disabled="desactivando"
          @change="alternar"
        >
        Anotar dónde fichas (opcional)
      </label>

      <template v-if="activo">
        <p
          v-if="centroDeclarado"
          class="texto-sm texto-suave"
        >
          Declarado el {{ formatearFecha(centroDeclarado.declaradoEn.slice(0, 10)) }}. Esta
          fecha aparece en tus informes.
          <template v-if="centroDeclarado.precisionMetros !== null">
            Precisión aproximada: ±{{ centroDeclarado.precisionMetros }} m.
          </template>
        </p>
        <p
          v-if="mostrarAvisoPermiso"
          class="nota texto-suave texto-sm"
          role="status"
        >
          Llevas varios fichajes sin conseguir tu ubicación: puede que Android
          haya revocado el permiso. Actívalo de nuevo desde los ajustes del
          sistema si quieres seguir anotándola.
        </p>
        <button
          type="button"
          class="boton-secundario"
          :disabled="borrandoHistorico"
          @click="borrarHistorico"
        >
          {{ borrandoHistorico ? 'Borrando...' : 'Borrar mi histórico de ubicaciones' }}
        </button>
        <p
          v-if="historicoBorrado"
          class="texto-sm texto-suave"
          role="status"
        >
          Histórico borrado. Tu diario de fichajes sigue intacto.
        </p>
      </template>
    </template>

    <!-- Pantalla explicativa: ANTES de cualquier diálogo del sistema. -->
    <template v-else-if="!permisoConcedido">
      <h2 class="titulo-seccion">
        Anotar dónde fichas
      </h2>
      <ul class="checklist">
        <li>
          <strong>Finalidad:</strong> acompañar tu registro horario con una
          anotación de que estabas en tu centro de trabajo.
        </li>
        <li>
          <strong>Base jurídica:</strong> tu consentimiento inequívoco y
          expreso (art. 6.1.a RGPD). La app funciona entera sin esto.
        </li>
        <li>
          <strong>Qué se envía y a dónde:</strong> tu posición aproximada
          sale de tu dispositivo y se guarda en el servidor de MeDeben.
        </li>
        <li>
          <strong>Conservación:</strong> hasta 15 meses desde cada fichaje,
          salvo que declares una reclamación en curso.
        </li>
        <li>
          Puedes <strong>revocarlo en un toque</strong>, cuando quieras.
        </li>
        <li>
          Puedes <strong>borrar todo tu histórico</strong> en un toque.
        </li>
        <li>
          <strong>Derechos:</strong> acceso, rectificación, supresión y
          portabilidad; puedes reclamar ante la AEPD.
        </li>
      </ul>
      <p class="aviso-anti-coaccion aviso-bloque">
        Tu empresa no puede exigirte activar esto ni entregarle el anexo con
        tus coordenadas. Si te lo piden, eso es control por geolocalización y
        debe cumplir el art. 90 de la LOPDGDD.
      </p>
      <RouterLink
        to="/privacidad"
        class="enlace-politica"
      >
        Leer la política completa
      </RouterLink>
      <label class="casilla-consentimiento">
        <input
          v-model="aceptoTexto"
          type="checkbox"
        >
        He leído esto y doy mi consentimiento
      </label>
      <p
        v-if="errorConsentimiento"
        class="aviso-bloque"
        role="alert"
      >
        {{ errorConsentimiento }}
      </p>
      <p
        v-if="permisoDenegadoPermanente"
        class="nota texto-suave texto-sm"
        role="status"
      >
        Android ha bloqueado el permiso de ubicación. Se activa desde los
        ajustes del sistema.
      </p>
      <div class="acciones">
        <button
          type="button"
          class="boton-secundario"
          @click="cancelarActivacion"
        >
          Cancelar
        </button>
        <button
          type="button"
          class="boton"
          :disabled="!aceptoTexto || enviandoConsentimiento || pidiendoPermiso"
          @click="confirmarConsentimiento"
        >
          {{ enviandoConsentimiento || pidiendoPermiso ? 'Un momento...' : 'Aceptar y continuar' }}
        </button>
      </div>
    </template>

    <!-- Permiso concedido: falta declarar el centro estando allí. -->
    <template v-else>
      <h2 class="titulo-seccion">
        Marca tu centro de trabajo
      </h2>
      <p class="texto-sm texto-suave">
        Púlsalo estando en el trabajo: es el punto con el que se compara cada
        fichaje al momento.
      </p>
      <button
        type="button"
        class="boton"
        :disabled="marcandoCentro"
        @click="marcaElCentro"
      >
        {{ marcandoCentro ? 'Localizando...' : 'Estoy en el trabajo, márcalo aquí' }}
      </button>
      <p
        v-if="errorCentro"
        class="aviso-bloque"
        role="alert"
      >
        {{ errorCentro }}
      </p>
    </template>
  </section>
</template>

<style scoped>
.fila-activar {
  display: flex;
  align-items: center;
  gap: var(--esp-xs);
  font-weight: var(--peso-etiqueta);
}

.checklist {
  display: flex;
  flex-direction: column;
  gap: var(--esp-xs);
  padding-left: var(--esp-md);
  font-size: var(--tipo-sm);
}

.acciones {
  display: flex;
  gap: var(--esp-sm);
}

.acciones > * {
  flex: 1;
}

.casilla-consentimiento {
  display: flex;
  align-items: center;
  gap: var(--esp-xs);
}
</style>
