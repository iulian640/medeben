<script setup lang="ts">
import DocumentoLegal from '../components/DocumentoLegal.vue'

/**
 * Términos de uso (transcritos de docs/legal/terminos.md). Contenido estático:
 * HTML semántico, sin v-html ni datos de usuario.
 */
</script>

<template>
  <DocumentoLegal
    titulo="Términos de uso"
    version="Versión 1.0 — 12 de julio de 2026"
  >
    <h2>1. Aceptación</h2>
    <p>
      Al usar MeDeben aceptas estos términos. Debes ser
      <strong>mayor de edad</strong>.
    </p>

    <h2>2. El servicio</h2>
    <p>
      MeDeben es una herramienta <strong>gratuita</strong> para que la persona
      trabajadora registre su jornada y estime lo que pudieran adeudarle. Es y
      será gratuita para el trabajador: no hay pago, ni publicidad, ni venta de
      tus datos.
    </p>

    <h2>3. Tu cuenta</h2>
    <p>
      Eres responsable de la seguridad de tus credenciales y de la veracidad de
      los datos que introduces. Avísanos si detectas un uso no autorizado.
    </p>

    <h2>4. Uso aceptable</h2>
    <p>
      MeDeben es para <strong>tu propia reclamación</strong>. Queda
      <strong>prohibido</strong> usarla para difamar a empresas o personas, o
      para publicar datos de terceros. No hay reseñas públicas de empresas ni
      listas negras.
    </p>

    <h2>5. Naturaleza de los cálculos</h2>
    <p>
      Los importes son <strong>orientativos</strong>, basados en las tablas del
      convenio y su vigencia, y pueden contener errores o no reflejar tu caso
      concreto. <strong>Verifica con un profesional o tu sindicato antes de
        reclamar.</strong>
    </p>

    <h2>6. Propiedad intelectual</h2>
    <p>
      El código se ofrece bajo la licencia <strong>AGPL-3.0</strong>
      (<a
        href="https://github.com/iulian640/medeben"
        target="_blank"
        rel="noopener noreferrer"
      >github.com/iulian640/medeben</a>). La marca «MeDeben» pertenece a su
      titular.
    </p>

    <h2>7. Limitación de responsabilidad</h2>
    <p>
      MeDeben no garantiza el resultado de ninguna reclamación ni sustituye el
      asesoramiento jurídico profesional. Dentro de lo permitido por la ley, el
      titular no responde de los daños derivados del uso de la herramienta o de
      decisiones tomadas a partir de sus cálculos.
    </p>

    <h2>8. Baja y borrado</h2>
    <p>
      Puedes eliminar tu cuenta en cualquier momento desde la app o desde
      <RouterLink to="/borrar-cuenta">
        medeben.net/borrar-cuenta
      </RouterLink>. <strong>El borrado destruye tu historial (es tu
        evidencia): descarga antes tu informe en PDF si lo necesitas.</strong>
    </p>

    <h2>9. Cambios y ley aplicable</h2>
    <p>
      Podemos modificar estos términos; avisaremos de los cambios relevantes. Se
      rigen por la legislación española.
    </p>
  </DocumentoLegal>
</template>
