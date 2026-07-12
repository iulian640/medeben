<script setup lang="ts">
import DocumentoLegal from '../components/DocumentoLegal.vue'

/**
 * Aviso legal (transcrito de docs/legal/aviso-legal.md). Contenido estático:
 * HTML semántico, sin v-html ni datos de usuario.
 */
</script>

<template>
  <DocumentoLegal
    titulo="Aviso legal"
    version="Versión 1.0 — 12 de julio de 2026"
  >
    <h2>1. Identificación del responsable</h2>
    <ul>
      <li><strong>Titular:</strong> Iulian Timofei</li>
      <li>
        <strong>Correo de contacto:</strong>
        <a href="mailto:iuliantim21@gmail.com">iuliantim21@gmail.com</a>
      </li>
    </ul>

    <h2>2. Objeto</h2>
    <p>
      MeDeben es una herramienta <strong>gratuita</strong> dirigida a la persona
      trabajadora para registrar su jornada y horas extra y estimar, de forma
      orientativa, las cantidades que pudieran adeudársele según el convenio
      colectivo aplicable.
    </p>

    <h2>3. Condiciones de acceso y uso</h2>
    <p>
      El acceso es gratuito y de uso personal. La persona usuaria se compromete a
      hacer un uso conforme a la ley y a estos términos, y es responsable de la
      veracidad de los datos que introduce.
    </p>

    <h2>4. Propiedad intelectual e industrial</h2>
    <p>
      El código fuente de MeDeben se publica bajo la licencia
      <strong>AGPL-3.0</strong> en
      <a
        href="https://github.com/iulian640/medeben"
        target="_blank"
        rel="noopener noreferrer"
      >github.com/iulian640/medeben</a>. La denominación «MeDeben» y los signos
      distintivos pertenecen a su titular. Las tablas de convenios proceden de
      boletines oficiales (sin derechos de autor, art. 13 LPI).
    </p>

    <h2>5. Exclusión de responsabilidad</h2>
    <p>
      Los cálculos que ofrece MeDeben son <strong>orientativos</strong>. El
      titular no garantiza el resultado de ninguna reclamación ni sustituye el
      asesoramiento de un profesional.
    </p>

    <h2>6. Protección de datos</h2>
    <p>
      El tratamiento de datos personales se rige por la
      <RouterLink to="/privacidad">
        Política de privacidad
      </RouterLink>.
    </p>

    <h2>7. Legislación aplicable</h2>
    <p>
      Este aviso se rige por la legislación española. Para cualquier
      controversia, las partes se someten a los juzgados y tribunales que
      correspondan conforme a derecho.
    </p>
  </DocumentoLegal>
</template>
