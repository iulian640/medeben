# Clasificación de contenido IARC — borrador para Play Console

> Respuestas al cuestionario de **clasificación de contenido (IARC)** de Google
> Play Console. El cuestionario es **obligatorio**; falsearlo puede acarrear
> retirada o suspensión (research punto 6).
> Fuente: https://support.google.com/googleplay/android-developer/answer/188189
>
> MeDeben es una app de **utilidad / productividad laboral** sin ningún contenido
> sensible. La respuesta a todo lo temático es **No**. Contesta con honestidad
> exactamente esto.

---

## Paso 0 — Datos previos

- **Email de contacto IARC:** usar el email de soporte dedicado (recomendado
  `soporte@medeben.net`, no el personal). Solo lo ve IARC, no el público.
- **Categoría de la app:** **"Todas las demás apps"** (Utility / Productivity /
  Reference), **NO** "Juego". MeDeben no es un juego, así que el cuestionario que
  toca es el de apps, no el de games.

---

## Cuestionario (todas las respuestas: NO, salvo la última)

**1 · Violencia** — ¿La app contiene violencia (realista, cartoon, fantástica,
sangre, etc.)?
- **No.** Nota: es una app para consultar convenios y registrar horas. Sin
  ningún contenido violento.

**2 · Miedo / terror** — ¿Contiene contenido que pueda asustar o perturbar?
- **No.**

**3 · Sexo y desnudez** — ¿Contiene contenido sexual, desnudez o insinuaciones?
- **No.**

**4 · Lenguaje** — ¿Contiene lenguaje soez, groserías o vulgaridades?
- **No.** Nota: los textos son informativos y neutros (convenios, nóminas, horas).

**5 · Sustancias controladas** — ¿Hace referencia o muestra drogas, alcohol o
tabaco, o fomenta su consumo?
- **No.**

**6 · Juegos de azar (gambling)** —
- ¿Contiene **juego con dinero real** (apuestas)? **No.**
- ¿Simula juego de azar (casino, tragaperras, etc.)? **No.**
- Nota: MeDeben calcula horas extra impagadas; no es un producto financiero ni de
  apuestas (research punto 10: Financial Services policy NO aplica).

**7 · Interacción entre usuarios** — ¿Los usuarios pueden interactuar o
comunicarse entre sí, o intercambiar/compartir contenido (chat, foros, perfiles
públicos, contenido generado por usuarios que otros vean)?
- **No.** Nota clave: **no hay chat, ni foros, ni perfiles públicos, ni ningún
  contenido compartido entre usuarios.** Cada cuenta es privada e individual; los
  datos de un usuario no los ve ningún otro. (La foto de cuadrante con datos de
  terceros **no está implementada**, ver `cumplimiento-lanzamiento.md`.)

**8 · Compartir la ubicación del usuario** — ¿La app comparte la ubicación
física actual del usuario con otros usuarios?
- **No.** Nota: desde que existe «Anotar dónde fichas» (ADR D39, opcional y
  apagada de fábrica), la app **sí** puede pedir permiso de ubicación
  aproximada al usuario que la activa. Pero esta pregunta es sobre
  **compartir** la ubicación **con otros usuarios**, no sobre recogerla: esa
  ubicación no la ve nadie más que el propio titular (y, si genera el anexo
  técnico, quien él decida entregárselo); no hay perfiles públicos ni
  contenido visible entre usuarios (ver pregunta 7). La provincia del perfil,
  aparte y sin relación con esta función, la teclea el usuario y tampoco se
  comparte con nadie.

**9 · Compras digitales** — ¿Permite comprar bienes o servicios digitales?
- **No.** Nota: la app es gratuita y no tiene compras in-app. (Si en el futuro se
  añade un botón de **donaciones**, las donaciones están exentas de Google Play
  Billing y **no** son "compras digitales" a efectos de esta pregunta; aun así,
  revisar el cuestionario si se añade.)

**10 · Contenido generado por usuarios visible para otros / moderación** —
¿La app muestra contenido generado por usuarios a otros usuarios?
- **No.** Nota: todo lo que el usuario introduce (horas, motivo, perfil) es
  privado y solo suyo.

**11 · Controles digitales / misceláneos** — ¿Contiene contenido que fomente
actividades ilegales, discriminación, etc.?
- **No.**

**12 · ¿La app está dirigida principalmente a menores?**
- **No.** Nota: MeDeben está dirigida a personas trabajadoras **mayores de edad**
  (privacidad §10). No es una app para niños ni participa en "Diseñada para
  familias".

---

## Clasificación esperada

Con todas las respuestas temáticas en **No** y sin interacción entre usuarios ni
compras, la clasificación que emiten las autoridades vía IARC debería ser la
**mínima en todos los territorios**:

| Autoridad / región | Clasificación esperada |
|---|---|
| PEGI (Europa) | **PEGI 3** |
| ESRB (Norteamérica) | **Everyone** |
| USK (Alemania) | **USK 0 / Sin restricción** |
| ClassInd (Brasil) | **Livre (L)** |
| Google Play (genérica) | **Apta para mayores de 3 años (3+)** |

- Nota: la clasificación la calculan y emiten las autoridades a partir de estas
  respuestas; no la elige el desarrollador. Con este perfil, "PEGI 3 / Everyone"
  es el resultado esperado.

---

## Después de enviar

- Revisar el **resumen** que genera IARC antes de confirmar y enviar.
- Si más adelante se añaden funciones sociales, compras o donaciones con flujo de
  pago, **rehacer el cuestionario** (las respuestas 7, 9 y 10 podrían cambiar).
