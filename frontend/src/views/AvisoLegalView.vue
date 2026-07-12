<script setup lang="ts">
import DocumentoLegal from '../components/DocumentoLegal.vue'

/**
 * Aviso legal (transcrito de docs/legal/aviso-legal.md). Contenido estático:
 * HTML semántico, sin v-html ni datos de usuario.
 *
 * Identificación LSSI (art. 10): el Titular y el correo son públicos y van
 * estáticos. El NIF y el domicilio a efectos de notificaciones son SENSIBLES:
 * se inyectan al build de despliegue vía frontend/.env.local (gitignored) y
 * NUNCA se versionan. En el repo público / CI las variables no existen, así que
 * en su lugar se muestra un texto de reserva neutro que remite a la versión
 * publicada. Nada de datos vacíos ni de fallos si faltan.
 */
// import.meta.env.VITE_* es un valor de build (no reactivo): un const simple
// basta. Si la variable no está inyectada, cae al texto de reserva neutro.
const TEXTO_RESERVA =
  'Disponible en la versión publicada en medeben.net/aviso-legal (no se incluye en este repositorio público)'

const nif = import.meta.env.VITE_RESPONSABLE_NIF || TEXTO_RESERVA
const domicilio = import.meta.env.VITE_RESPONSABLE_DOMICILIO || TEXTO_RESERVA
</script>

<template>
  <DocumentoLegal
    titulo="Aviso legal"
    version="Versión 1.1 — 12 de julio de 2026"
  >
    <h2>1. Identificación del responsable</h2>
    <p>
      MeDeben acepta donaciones voluntarias (ver el punto 8), lo que constituye
      actividad económica a efectos del <strong>art. 10 de la LSSI-CE</strong>.
      Por ello se publican los siguientes datos identificativos:
    </p>
    <ul>
      <li><strong>Titular:</strong> Iulian Timofei</li>
      <li>
        <strong>Correo de contacto:</strong>
        <a href="mailto:iuliantim21@gmail.com">iuliantim21@gmail.com</a>
      </li>
      <li><strong>NIF:</strong> {{ nif }}</li>
      <li>
        <strong>Domicilio a efectos de notificaciones:</strong> {{ domicilio }}
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

    <h2>8. Donaciones</h2>
    <p>
      MeDeben es y seguirá siendo <strong>gratuita</strong>. De forma
      <strong>voluntaria</strong>, quien lo desee puede hacer una donación de
      apoyo a través de <strong>Ko-fi</strong>, una plataforma externa. La
      donación es un simple gesto de apoyo: <strong>no otorga ningún derecho ni
        contraprestación</strong>, no da acceso a funciones extra ni retira
      publicidad (no la hay). El cobro lo procesa íntegramente Ko-fi, con sus
      propios términos y política de privacidad; MeDeben no recibe ni guarda
      datos de tu pago.
    </p>
  </DocumentoLegal>
</template>
