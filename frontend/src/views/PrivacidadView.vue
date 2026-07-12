<script setup lang="ts">
import DocumentoLegal from '../components/DocumentoLegal.vue'

/**
 * Política de privacidad (transcrita de docs/legal/privacidad.md). Contenido
 * estático: HTML semántico, sin v-html ni datos de usuario.
 */
</script>

<template>
  <DocumentoLegal
    titulo="Política de privacidad"
    version="Versión 1.0 — 12 de julio de 2026"
  >
    <h2>1. Responsable del tratamiento</h2>
    <ul>
      <li><strong>Responsable:</strong> Iulian Timofei.</li>
      <li>
        <strong>Contacto de privacidad:</strong>
        <a href="mailto:iuliantim21@gmail.com">iuliantim21@gmail.com</a>.
      </li>
    </ul>

    <h2>2. Qué datos tratamos</h2>
    <ul>
      <li>
        <strong>Cuenta:</strong> correo electrónico y contraseña (guardada solo
        como hash, nunca en claro).
      </li>
      <li>
        <strong>Perfil laboral:</strong> provincia, convenio, puesto y salario
        que tú indicas.
      </li>
      <li>
        <strong>Jornada:</strong> tus fichajes de entrada/salida, horario y
        ausencias.
      </li>
      <li>
        <strong>Motivo de ausencia (opcional):</strong> es texto libre y
        <strong>puede revelar datos de salud</strong> (p. ej. «enfermo»). Se
        trata como <strong>categoría especial (art. 9 RGPD)</strong>; es
        opcional y puedes dejarlo en blanco.
      </li>
    </ul>
    <p>
      No pedimos ni almacenamos tu nombre, DNI ni teléfono. No usamos analítica
      de terceros.
    </p>

    <h2>3. Para qué (finalidades)</h2>
    <p>
      Registrar tu jornada, calcular de forma orientativa lo que pudieran
      adeudarte, y generar un informe que puedas usar como prueba en tu propia
      reclamación.
    </p>

    <h2>4. Base jurídica</h2>
    <ul>
      <li>
        <strong>Art. 6.1.b RGPD</strong> (ejecución del servicio que solicitas)
        para los datos de cuenta, perfil y jornada.
      </li>
      <li>
        <strong>Art. 9.2.f RGPD</strong> (formulación y defensa de
        reclamaciones) para el posible dato de salud del motivo de ausencia.
      </li>
    </ul>

    <h2>5. Conservación</h2>
    <p>
      Mientras mantengas tu cuenta. Si la eliminas, tus datos se
      <strong>borran de verdad</strong> (no hay copia oculta). Como referencia,
      las reclamaciones laborales prescriben al año (art. 59 ET).
    </p>

    <h2>6. Destinatarios y encargados</h2>
    <p>
      Alojamos los datos en <strong>Hetzner Online GmbH</strong>, con servidores
      en Alemania (<strong>Unión Europea</strong>) y contrato de encargado de
      tratamiento (art. 28 RGPD).
      <strong>No vendemos ni cedemos tus datos a nadie.</strong> No hay
      transferencias internacionales.
    </p>

    <h2>7. Tus derechos</h2>
    <p>
      Puedes ejercer <strong>acceso, rectificación, supresión, portabilidad,
        limitación y oposición</strong>, y <strong>reclamar ante la AEPD</strong>
      (<a
        href="https://www.aepd.es"
        target="_blank"
        rel="noopener noreferrer"
      >www.aepd.es</a>). Escribe a
      <a href="mailto:iuliantim21@gmail.com">iuliantim21@gmail.com</a>;
      responderemos en el plazo de un mes. Para el borrado total también puedes
      usar la opción dentro de la app o la página
      <RouterLink to="/borrar-cuenta">
        medeben.net/borrar-cuenta
      </RouterLink>.
    </p>

    <h2>8. Seguridad</h2>
    <p>
      Ciframos la comunicación (HTTPS), el credencial de sesión vive solo en
      memoria del dispositivo, y hacemos copias de seguridad.
    </p>

    <h2>9. Cookies y almacenamiento local</h2>
    <p>
      Solo usamos almacenamiento <strong>técnico estrictamente necesario</strong>
      (mantener tu sesión y tus preferencias). <strong>No usamos cookies
        sujetas a consentimiento</strong> ni rastreadores de terceros.
    </p>

    <h2>10. Menores</h2>
    <p>MeDeben está dirigida a personas trabajadoras mayores de edad.</p>

    <h2>11. Donaciones</h2>
    <p>
      Si decides hacer una donación voluntaria, el pago lo procesa
      <strong>Ko-fi</strong> (plataforma externa, con su propia política de
      privacidad). MeDeben <strong>no recibe ni guarda</strong> datos de tu pago
      (tarjeta, importe o identidad del donante).
    </p>
  </DocumentoLegal>
</template>
