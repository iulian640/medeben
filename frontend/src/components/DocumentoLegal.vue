<script setup lang="ts">
import EnlacesLegales from './EnlacesLegales.vue'

/**
 * Chasis común de las páginas legales de solo lectura (privacidad, términos,
 * aviso legal): columna centrada, tipografía de documento y el pie con los
 * enlaces cruzados. El contenido va en el slot como HTML estático y semántico
 * (h2/p/ul/strong) transcrito de docs/legal — nunca datos de usuario, así que
 * jamás hace falta v-html.
 */
defineProps<{
  titulo: string
  /** Nota de versión/estado que va bajo el título, en letra menor. */
  version?: string
}>()
</script>

<template>
  <main class="doc-legal">
    <article class="cuerpo">
      <header class="cabecera">
        <h1>{{ titulo }}</h1>
        <p
          v-if="version"
          class="version texto-sm texto-suave"
        >
          {{ version }}
        </p>
      </header>
      <slot />
    </article>

    <footer class="pie">
      <EnlacesLegales />
      <p class="texto-sm">
        <RouterLink to="/">
          Volver al inicio
        </RouterLink>
      </p>
    </footer>
  </main>
</template>

<style scoped>
.doc-legal {
  max-width: 42rem;
  margin: 0 auto;
  padding: var(--esp-lg) var(--esp-md) var(--esp-2xl);
  display: flex;
  flex-direction: column;
  gap: var(--esp-xl);
}

.cabecera {
  display: flex;
  flex-direction: column;
  gap: var(--esp-2xs);
}

h1 {
  font-size: var(--tipo-titulo);
}

.cuerpo {
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
}

/* Ritmo de documento sobre el contenido del slot (Vue exige :slotted para
 * llegar a los nodos que inyecta la página). Titulares de sección con aire
 * arriba, párrafos y listas legibles, negritas con peso de etiqueta. */
.cuerpo :slotted(h2) {
  font-size: var(--tipo-lg);
  margin-top: var(--esp-md);
}

.cuerpo :slotted(h2:first-child) {
  margin-top: 0;
}

.cuerpo :slotted(p),
.cuerpo :slotted(ul) {
  margin: 0;
}

.cuerpo :slotted(ul) {
  padding-left: var(--esp-lg);
  display: flex;
  flex-direction: column;
  gap: var(--esp-2xs);
}

.cuerpo :slotted(li) {
  padding-left: var(--esp-2xs);
}

.cuerpo :slotted(strong) {
  font-weight: var(--peso-etiqueta);
}

.pie {
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
  align-items: center;
  border-top: 1px solid var(--linea);
  padding-top: var(--esp-lg);
}
</style>
