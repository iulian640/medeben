<script setup lang="ts">
import { computed } from 'vue'

/**
 * Disclaimer C5, punto 1 (docs/legal/disclaimers.md): el texto va SIEMPRE bajo
 * la cifra estimada, nunca en un modal. [nombre]/[año]/[boletín] llegan del
 * convenio real del perfil (D34: nunca se inventan). Sin boletín (la fuente
 * del convenio no lo trae tipado), se omite en vez de rellenarlo.
 *
 * Texto SIEMPRE interpolado ({{ }}), nunca v-html: nombre y boletín viajan
 * desde el backend, pero la regla de seguridad es no interpretar HTML jamás.
 */
const props = defineProps<{
  nombre: string
  anio: string
  boletin: string | null
}>()

const referencia = computed(() => (props.boletin ? `${props.anio}, ${props.boletin}` : props.anio))
</script>

<template>
  <p class="disclaimer-calculo texto-xs texto-suave">
    Cálculo <strong>orientativo</strong> según las tablas del convenio
    {{ nombre }} ({{ referencia }}). Puede contener errores o no reflejar tu
    situación concreta.
    <strong>Verifica con un profesional o tu sindicato antes de reclamar.</strong>
  </p>
</template>
