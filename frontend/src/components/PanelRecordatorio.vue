<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import {
  cancelarNotificaciones,
  programarNotificaciones,
  solicitarPermisoNotificaciones,
} from '../lib/notificaciones'
import {
  guardaHoraRecordatorio,
  horaRecordatorio,
  idsRecordatorio,
  planRecordatorios,
} from '../lib/recordatorio'

/**
 * Recordatorio diario de fichar, SOLO en la app nativa (Android): en el
 * navegador las notificaciones programadas no son fiables y no se ofrece.
 * La preferencia es solo la hora ("21:30"); ningún dato personal.
 */
const esNativo = Capacitor.isNativePlatform()

const activo = ref(false)
const hora = ref('21:30')
const sinPermiso = ref(false)
const ocupado = ref(false)

onMounted(async () => {
  if (!esNativo) {
    return
  }
  const guardada = horaRecordatorio()
  if (guardada) {
    activo.value = true
    hora.value = guardada
    // Renueva la quincena en silencio: los ids se reutilizan (idempotente).
    await programarNotificaciones(planRecordatorios(guardada, new Date()))
  }
})

async function alternar(event: Event) {
  const marcado = (event.target as HTMLInputElement).checked
  if (ocupado.value) {
    return
  }
  ocupado.value = true
  try {
    if (marcado) {
      sinPermiso.value = false
      const concedido = await solicitarPermisoNotificaciones()
      if (!concedido) {
        // Sin permiso no hay recordatorio: se dice claro y se deja apagado.
        sinPermiso.value = true
        activo.value = false
        return
      }
      activo.value = true
      guardaHoraRecordatorio(hora.value)
      await programarNotificaciones(planRecordatorios(hora.value, new Date()))
    } else {
      activo.value = false
      guardaHoraRecordatorio(null)
      await cancelarNotificaciones(idsRecordatorio())
    }
  } finally {
    ocupado.value = false
  }
}

async function cambiaHora(event: Event) {
  const nueva = (event.target as HTMLInputElement).value
  if (nueva === '' || !activo.value) {
    hora.value = nueva === '' ? hora.value : nueva
    return
  }
  hora.value = nueva
  guardaHoraRecordatorio(nueva)
  await programarNotificaciones(planRecordatorios(nueva, new Date()))
}
</script>

<template>
  <section
    v-if="esNativo"
    class="recordatorio tarjeta"
    aria-labelledby="recordatorio-titulo"
  >
    <label
      id="recordatorio-titulo"
      class="fila-activar"
    >
      <input
        type="checkbox"
        :checked="activo"
        :disabled="ocupado"
        @change="alternar"
      >
      Recuérdame fichar cada día
    </label>

    <label
      v-if="activo"
      class="fila-hora"
    >
      A las
      <input
        type="time"
        :value="hora"
        @change="cambiaHora"
      >
    </label>

    <p
      v-if="sinPermiso"
      class="nota texto-suave texto-sm"
      role="status"
    >
      Sin permiso de notificaciones no puedo recordártelo. Puedes dárselo a la
      app en los ajustes de Android.
    </p>
  </section>
</template>

<style scoped>
.fila-activar {
  display: flex;
  align-items: center;
  gap: var(--esp-xs);
  font-weight: var(--peso-etiqueta);
}

.fila-hora {
  display: flex;
  align-items: center;
  gap: var(--esp-xs);
}

/* Sin un .campo (label encima) que envolver: es una etiqueta en línea
 * ("A las [hora]"), así que el control toma el mismo vocabulario a mano. */
.fila-hora input {
  font: inherit;
  color: var(--tinta);
  background: var(--papel);
  min-height: 2.75rem;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--linea-fuerte);
  border-radius: var(--radio-control);
}
</style>
