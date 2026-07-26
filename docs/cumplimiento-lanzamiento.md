# Cumplimiento legal — checklist de lanzamiento (MeDeben)

> Mapa de riesgos accionable, **no un dictamen jurídico**. Antes de abrir a
> usuarios reales, los documentos legales y la EIPD deberían pasar la revisión
> de un experto en protección de datos / laboralista (plan del ADR D11).
> Investigado 2026-07-11 contra fuentes oficiales (RGPD/LOPDGDD, AEPD, LSSI-CE,
> ayuda de Google Play). Fuentes al final.

## Alcance verificado (buena noticia)

La **foto del cuadrante con datos de compañeros** —el mayor riesgo RGPD que
anticipaba el ADR D11.1 (datos de terceros)— **todavía NO está implementada**:
`Cuadrante.java` es un horario en texto append-only, no una imagen. Ese flanco
**no aplica al lanzamiento actual**. Si se añade la foto más adelante, hay que
rehacer el análisis (base jurídica de terceros, minimización, difuminado de
nombres) antes de activarla.

**Base jurídica que hay que declarar:** art. 6.1.b (ejecución de un servicio
pedido por el propio usuario) para lo nuclear; **art. 9.2.f (defensa de
reclamaciones) para el `motivo` de ausencia** (posible dato de salud). El código
ya eligió el 9.2.f y es el encaje correcto.

---

## (A) YA CUMPLIDO — con evidencia en el código

| # | Requisito | Evidencia | Norma |
|---|-----------|-----------|-------|
| A1 | Minimización de datos | `Usuario.java`: solo email + hash + fecha. `Perfil.java`: solo datos laborales. Sin nombre/teléfono/DNI. | art. 5.1.c, 25 |
| A2 | Borrado de cuenta (supresión) | `DELETE /api/v1/cuenta`: borrado REAL con `ON DELETE CASCADE`, re-confirma contraseña, purga el PDF cacheado. **Cubre también el "borrar cuenta in-app" de Google Play.** | art. 17 |
| A3 | Minimización del dato de salud | `motivo` SOLO en AUSENCIA (máx. 200 car.), redactado en `toString()`, nunca logueado, base 9.2.f documentada. | art. 9, 25 |
| A4 | Rectificación | `PUT /api/v1/perfil`; diario con rectificación tardía sellada (D38). | art. 16 |
| A5 | Seguridad de credenciales | Access token solo en memoria; refresh rotativo con detección de robo; logout revoca en servidor; rate limiting por IP. | art. 32 |
| A6 | Cifrado en tránsito + cabeceras | HSTS/CSP/nosniff/frame-deny/referrer/permissions; HTTPS obligatorio (Caddy); backend solo en 127.0.0.1. | art. 32 |
| A7 | Backups + integridad probatoria | `pg_dump` diario con copia externa y ensayo de restauración; sello de fecha + diario append-only. | buena práctica |

---

## (B) BLOQUEANTE para lanzar

- **B1 · Los 4 documentos legales** (no existe ninguno). Aviso legal, política de
  privacidad, términos, disclaimers. Plantillas en `docs/legal/`. La **política
  de privacidad es requisito de Google Play** (URL pública) y del art. 13 RGPD.
- **B2 · Información al interesado en el registro** (art. 13). Antes de crear la
  cuenta hay que mostrar responsable, fines, base jurídica (6.1.b + 9.2.f),
  conservación y derechos (o enlace visible a la política). Hoy el registro solo
  pide email + contraseña.
- **B3 · URL pública de borrado de cuenta** (Google Play). Además del borrado
  in-app (ya hecho), Play exige una **página web** (p. ej. `medeben.net/borrar-cuenta`)
  para pedir el borrado aunque se haya desinstalado. Falta la página; el endpoint
  ya existe.
- **B4 · Cuestionario "Seguridad de los datos"** de Play Console. Declarar email,
  **dato de salud** (el `motivo`), cifrado en tránsito y mecanismo de borrado.
  Coherente con la política.
- **B5 · Registro de Actividades de Tratamiento (RAT, art. 30).** Obligatorio: la
  excepción <250 personas **no aplica** al tratar categorías especiales (salud).
  Documento interno.
- **B6 · Evaluación de Impacto (EIPD/DPIA, art. 35).** Obligatoria: concurren dos
  criterios de la lista AEPD — **datos de salud** + **colectivo vulnerable**
  (trabajadores). Arrancar con la herramienta *Gestiona EIPD* de la AEPD; como
  mínimo dejarla iniciada y documentada antes de abrir a usuarios.
- **B7 · Encargado de tratamiento + ubicación UE (art. 28).** Firmar el DPA con el
  hosting/VPS y confirmar datos en la UE (Hetzner/OVH tienen DPA y datacenters UE).
  Elegir proveedor y guardar el DPA.
- **B8 · Aviso legal (LSSI art. 10).** Obligatorio **si hay botón de donaciones**
  (ADR D16 → actividad económica): identificar responsable (nombre, NIF, contacto).
  Como persona física, usar un domicilio a efectos de notificaciones + email, no
  el particular. Si al lanzar NO hay donaciones, deja de ser obligatorio por LSSI
  pero la identificación del responsable sigue haciendo falta en la privacidad.

---

## (C) RECOMENDABLE pronto (no bloquea si se atiende a mano)

- **C1 · Export completo de datos** (art. 15/20). Hoy solo hay PDF de horas, que
  no es un volcado estructurado de TODO. Recomendable un endpoint "descargar mis
  datos" (JSON). Mientras no exista, atender la solicitud **a mano en ≤1 mes**.
- **C2 · Procedimiento de brechas** (art. 33-34). Protocolo escrito: AEPD en <72 h
  y a los interesados si hay alto riesgo (lo hay, por el dato de salud).
- **C3 · Canal de derechos.** Email dedicado (p. ej. `privacidad@medeben.net`) en
  la política; respuesta en 1 mes.
- **C4 · Cifrado a nivel de columna del `motivo`** (art. 9). El ADR D38 ya lo
  aparca "antes de escalar". **OJO:** hasta que esto exista, el disclaimer del
  motivo NO debe decir "se guarda cifrada" (sería inexacto) — ver `docs/legal/disclaimers.md`.
- **C5 · Disclaimers en pantalla** ("orientativo" / "no es asesoramiento
  jurídico") en la cifra "te deben X€", el PDF y las guías. Textos en `docs/legal/disclaimers.md`.

---

## Cuestiones RESUELTAS (nada que hacer, documentado)

- **Banner de cookies — NO se necesita.** `localStorage` guarda solo el refresh
  token (técnico, estrictamente necesario → exento) y el flag de onboarding /
  preferencias del usuario (la Guía de Cookies AEPD mayo-2024 las considera
  técnicas). No hay analytics ni terceros. Basta una frase en la privacidad.
- **DPO — NO obligatorio.** No es autoridad pública ni hay tratamiento a gran
  escala de categorías especiales (art. 37.1.c). Revisar si crece mucho la base.

---

## (D) Bloqueante SOLO si se activa «Anotar dónde fichas» en Play (ADR D39)

Esta feature nace apagada de fábrica y no se sube a ninguna pista de Play
mientras el lanzamiento siga bloqueado por la verificación de dispositivo
Android físico. Cuando llegue el momento de subir un artefacto con el
permiso de ubicación, estos dos puntos son bloqueantes:

- **D1 · Verificación del manifest MERGEADO antes de cualquier subida.** El
  manifest merger de AGP hace la unión de permisos de la app y de sus
  librerías: `@capacitor/geolocation` declara `ACCESS_FINE_LOCATION` en su
  propio manifest aunque la app solo pida `ACCESS_COARSE_LOCATION`. Antes de
  subir a **cualquier** pista (incluida prueba interna), ejecutar
  `npx cap sync android && ./gradlew :app:processReleaseManifest` y leer
  `android/app/build/intermediates/merged_manifests/` para confirmar que
  aparece exactamente `ACCESS_COARSE_LOCATION` y que `ACCESS_FINE_LOCATION`
  se excluyó vía `tools:node="remove"`. Repetir esta comprobación tras
  cualquier actualización del plugin o de Capacitor.
- **D2 · Los textos legales van antes que el permiso.** Regla dura: los
  documentos legales y de Play (`docs/legal/privacidad.md` +
  `PrivacidadView.vue`, `docs/legal/disclaimers.md`,
  `docs/play/data-safety.md`, `docs/play/iarc.md`, `docs/play/ficha.md`) se
  publican **antes o en el mismo momento** que se sube un artefacto cuyo
  manifest mergeado contenga el permiso de ubicación — nunca después. Un
  revisor que abra el manifest y vea un permiso que la ficha de Data Safety
  declara inexistente es la vía rápida a la suspensión de la cuenta de
  desarrollador (ya advertido en la sección "Data Safety: la decisión de
  criterio" de la síntesis del diseño). Checklist mínimo antes de subir:
  - [ ] `docs/play/data-safety.md` fila "Ubicación" dice "Sí" (ya hecho, D39).
  - [ ] `privacidad.md` / `PrivacidadView.vue` mencionan la base 6.1.a y la
        retención de 15 meses (ya hecho, D39).
  - [ ] El manifest mergeado se ha leído y confirma D1.
  - [ ] RAT/EIPD reevaluados (checklist en `docs/ADR.md`, final de D39).

## Orden sugerido para el lanzamiento

1. Elegir VPS UE + firmar DPA (B7). 2. Redactar y publicar los 4 documentos
   (B1) + página de borrado (B3). 3. Poner el enlace a la privacidad en el
   registro (B2) y los disclaimers en pantalla (C5). 4. RAT (B5) y EIPD (B6)
   internos. 5. Rellenar Data Safety de Play (B4) coherente con la privacidad.
6. Que un experto revise los documentos y la EIPD antes de abrir a usuarios.

## Fuentes

- RGPD (UE 2016/679) arts. 5, 6, 9, 13, 15-17, 20, 28, 30, 32-35, 37; LOPDGDD 3/2018.
- AEPD: [Guía de cookies (mayo 2024)](https://www.aepd.es/guias/guia-cookies.pdf) · [Listas EIPD art. 35.4](https://www.aepd.es/documento/listas-dpia-es-35-4.pdf) · [RAT](https://www.aepd.es/en/rights-and-duties/fulfill-your-duties/measures-compliance/record-processing-activities).
- LSSI-CE (Ley 34/2002) art. 10 y 22.2.
- Google Play: [Borrado de cuenta](https://support.google.com/googleplay/android-developer/answer/13327111) · [Seguridad de los datos](https://support.google.com/googleplay/android-developer/answer/10787469).
