<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PanelPlegable from './PanelPlegable.vue'

/**
 * Panel de rectificación tardía de la libreta (D38): aparece cuando el backend
 * responde 409 (el día ya está sellado). Explica en cristiano qué implica y solo
 * deja reenviar el apunte como rectificación tardía tras marcar la casilla.
 *
 * La confirmación es estado LOCAL: al reenviar con éxito, el padre oculta este
 * panel (deja de haber conflicto) y el componente se desmonta, así que se
 * reinicia solo — no hace falta que el padre lo resetee.
 */
defineProps<{ fichando: boolean }>()

/**
 * El evento lleva el booleano del checkbox: el padre lo re-comprueba antes de
 * mandar la petición con valor probatorio (comprobación redundante a propósito
 * — el disabled del botón no es la única barrera).
 */
const emit = defineEmits<{ confirmar: [confirmado: boolean] }>()

const confirmado = ref(false)

/*
 * El padre monta este panel con v-if solo cuando hay conflicto (así se
 * desmonta y reinicia solo, ver arriba). PanelPlegable, aparte, empieza
 * cerrado y se abre un instante después del montaje: así el aviso se
 * despliega animado en vez de aparecer de golpe en mitad de la pantalla.
 */
const revelado = ref(false)
onMounted(() => {
  revelado.value = true
})
</script>

<template>
  <PanelPlegable :abierto="revelado">
    <section
      class="tarjeta"
      aria-labelledby="rectificacion-titulo"
    >
      <h2
        id="rectificacion-titulo"
        class="titulo-seccion"
      >
        Este día ya quedó protegido
      </h2>
      <p>
        Pasados 14 días, cada día de tu libreta queda protegido: lo apuntado se
        fija como prueba y ya no se reescribe, para que nadie pueda cambiarlo en
        tu contra.
      </p>
      <p>
        Aun así puedes registrarlo como <strong>rectificación tardía</strong>:
        se guarda aparte, con su propia fecha, y lo ya protegido no se toca. Como
        prueba vale menos que lo fichado al momento, pero es honesto y queda
        en tu libreta.
      </p>
      <label class="confirmar">
        <input
          v-model="confirmado"
          type="checkbox"
        >
        Entiendo que quedará registrado como rectificación tardía, separado
        del día ya protegido
      </label>
      <button
        type="button"
        class="boton-secundario"
        :disabled="!confirmado || fichando"
        @click="emit('confirmar', confirmado)"
      >
        Registrar la rectificación
      </button>
    </section>
  </PanelPlegable>
</template>

<style scoped>
.confirmar {
  display: flex;
  align-items: flex-start;
  gap: var(--esp-xs);
}
</style>
