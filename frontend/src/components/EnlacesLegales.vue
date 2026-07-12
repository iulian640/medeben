<script setup lang="ts">
/**
 * Enlaces discretos a las páginas legales de información. Se usan en el pie de
 * cada página legal y en la pantalla de login (la app no tiene footer global:
 * con sesión solo hay barra inferior). /borrar-cuenta no va aquí a propósito:
 * es una acción, no una política, y se alcanza desde el texto de privacidad y
 * términos y por su URL directa (la que registra Google Play).
 */
const paginas = [
  { a: '/privacidad', etiqueta: 'Privacidad' },
  { a: '/terminos', etiqueta: 'Términos' },
  { a: '/aviso-legal', etiqueta: 'Aviso legal' },
]
</script>

<template>
  <nav
    class="enlaces-legales texto-sm texto-suave"
    aria-label="Páginas legales"
  >
    <template
      v-for="(p, i) in paginas"
      :key="p.a"
    >
      <RouterLink
        class="enlace"
        :to="p.a"
      >
        {{ p.etiqueta }}
      </RouterLink>
      <span
        v-if="i < paginas.length - 1"
        class="separador"
        aria-hidden="true"
      >·</span>
    </template>
  </nav>
</template>

<style scoped>
.enlaces-legales {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--esp-xs);
}

.separador {
  color: var(--linea-fuerte);
}
</style>
