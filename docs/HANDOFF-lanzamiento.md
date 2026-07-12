# HANDOFF — Lanzamiento MeDeben

> Reescrito el 2026-07-12 (madrugada) por Claude Fable 5 al cerrar la sesión de
> preparación de lanzamiento. Sustituye al plan anterior: casi todo lo que aquel
> planificaba **ya está hecho**. Esto es el estado real + lo que queda, que es
> **de Iulian** (cuenta, identidad, dominio), no de código.

---

## 0. TL;DR

La app está **lista para lanzar por parte del código**: auditoría de seguridad
pasada, 3 bloqueantes de dinero/sesión arreglados, legal publicable, QA hostil
pasado, `.aab` firmado generado, y todo el papeleo de Play redactado para
copiar/pegar. Lo que falta son pasos que **solo puede dar Iulian** (crear la cuenta
de Play, verificar identidad, contratar el VPS, apuntar el DNS) y **un backup del
keystore que es urgente**.

`main` está limpio y sincronizado (último commit de la sesión: fix del BUG-1 del QA).
Suites verdes: backend + frontend. Sin worktrees ni ramas colgando.

---

## 0.1 Actualización — 12-jul (tarde)

Después del cierre inicial se hizo más trabajo, todo en `main` y con **CI en verde**:

- **Donaciones (Ko-fi), PR #255.** Iulian decidió lanzar CON donaciones vía `ko-fi.com/medeben`
  (donación pura, sin recompensa → exenta de Play Billing). Esto activa la **LSSI art. 10** →
  el aviso legal ahora publica su identificación. Para no meter su NIF/domicilio en el repo
  público, se **inyectan al build** desde `frontend/.env.local` (gitignored); en el repo solo van
  placeholders (`.env.example`). **Al desplegar la web o generar el `.aab` hay que tener ese
  `.env.local`** o el aviso legal sale sin dirección (incumpliría LSSI en prod).
- **Logo nuevo, PR #256.** Euro grueso en **ámbar** `#f5a623` (antes teal fino). Aplicado a
  favicon, PWA, iconos Android, splash, icono 512 y feature graphic. La **UI sigue teal** (el ámbar
  sobre blanco falla contraste AA). `.aab` regenerado.
- **CI arreglada, PR #257.** Todas las PRs de la sesión estaban con CI roja: no el código
  (Frontend+Backend verdes) sino el **E2E**, ahogado por el rate limit estricto de `/auth/registro`
  (registra ~10 cuentas seguidas desde la misma IP → 429). Fix: rate limit **off en el perfil dev**
  (prod lo mantiene). **Regla: verificar CI verde antes de mergear.**

**🚩 BLOQUEANTE PRINCIPAL DE PLAY = verificación de dispositivo Android.** Google exige a las
cuentas personales nuevas verificar acceso a un **Android FÍSICO** (Android 10+, no rooteado) con
la app *Google Play Console*. **El emulador NO vale** (confirmado en soporte oficial). Iulian no
tiene Android, así que **el carril de Play queda APARCADO** hasta que consiga uno (basta prestado
5 min, o un usado ~50 €). Los 12 testers usan sus propios móviles (hostelería ≈ todo Android). La
**web/PWA no depende de esto**. Iulian avisará cuando tenga el Android.

**Decisión sobre el VPS:** no montarlo en vacío. Crearlo el día que arranque el test cerrado o que
se comparta `medeben.net`, no antes (los ~6 €/mes con IVA cuentan desde que se crea). La clave SSH
para el deploy ya está generada en `~/.ssh/id_ed25519`.

---

## 1. Lo que se hizo esta noche (todo mergeado a `main`)

**Auditoría integral** (workflow `auditoria-lanzamiento.mjs`, 12 auditores + verificación
adversarial 2-de-3): 3 bloqueantes reales, todos arreglados con TDD:

- **B1 · Hora extra por convenio** (PR #250): el motor pagaba a hora ordinaria (−29-37%)
  en ~14 provincias porque solo leía la clave `importe`. Ahora resuelve `precioHora`
  (Almería) y devuelve **422 honesto** para las claves heterogéneas (Álava, Valencia,
  Vizcaya) en vez de una cifra falsa. Follow-up documentado.
- **B2 · Suelo del SMI** (PR #251): el resumen y el PDF valoraban con tablas por debajo
  del SMI sin suelo. Ahora `SmiService.aplicaSuelo` eleva la base al SMI del año y cita
  el art. 27 ET; PDF mensual y anual arrastran la corrección (verificado con PDFBox).
- **B3 · Guard de sesión** (PR #249): en tablet compartida, el 401+refresh reintentaba
  con el token de OTRO usuario (escritura cruzada). Cerrado con guarda de sesión.

**Quick wins** (PR #248): errores de red en castellano, año real en el PDF, rate limit
propio y estricto para `/auth/registro` (mitiga la enumeración de cuentas).

**Legal** (PRs #244, #245, #247, #252): 4 textos legales rellenos (sin donaciones →
público solo nombre + email; sin NIE ni domicilio), páginas web `/privacidad`,
`/terminos`, `/aviso-legal` y `/borrar-cuenta` (esta la exige Play), info art. 13 en
el registro, y disclaimers "orientativo" en la cifra, el PDF y el campo motivo.

**Play** (PR #246 + assets): research de requisitos, borradores de Data Safety e IARC
(`docs/play/`), textos de la ficha, e icono 512 + feature graphic generados.

**QA hostil** (Opus, emulador + navegador): 0 críticos, 0 altos, 1 medio (BUG-1,
arreglado PR #253), 2 bajos (documentados). ~15 vectores hostiles repelidos.

**Revisión final de seguridad** (Fable, skills springboot-security + security-review):
**aprobada, cero hallazgos nuevos**.

---

## 2. Documentos generados (fuera del repo, en `C:\Users\iulia\Documents\`)

- **`medeben-play-checklist.md`** — LO PRIMERO que debe leer Iulian: qué está hecho en
  Play y qué le toca a él, paso a paso.
- **`medeben-release\aab\medeben-1.0.0-v1.aab`** — el bundle firmado, listo para subir,
  con `LEEME-subida.md` (cómo subirlo + cómo generar la próxima versión).
- **`medeben-legal-interno\RAT.md` y `EIPD.md`** — documentos internos RGPD (con NIE y
  domicilio; NO subir al repo público). EIPD: riesgo residual aceptable, condicionado.
- **`medeben-revision-seguridad-final.md`** — el informe de la revisión de seguridad.
- **`medeben-qa-informe.md`** + `Pictures\medeben-qa\` (capturas, incl. las de la ficha).
- **`medeben-pendientes-auditoria.md`** — los 6 hallazgos NO bloqueantes + BUG-2/BUG-3
  del QA, para el post-lanzamiento. **Privado**: no publicar como issues sin redactar
  (el clasificador frenó publicarlos en el repo público; con razón).
- **`MeDeben-donaciones-conclusion.txt`** — mapa de decisión sobre donaciones.
- **`Pictures\medeben-play\`** — icono 512 + feature graphic para la ficha.

---

## 3. Lo que le toca a IULIAN (por orden)

Detalle completo en `medeben-play-checklist.md`. Resumen:

1. **⚠️ BACKUP DEL KEYSTORE hoy mismo.** `C:\Users\iulia\Documents\medeben-release\`
   fuera del portátil (gestor de contraseñas + disco externo). Sin él no hay
   actualizaciones futuras en Play.
2. **Email del dominio:** montar el reenvío para que `privacidad@`/`contacto@medeben.net`
   lleguen a tu Gmail (opcional para el día 1 — la privacidad ya usa iuliantim21@gmail.com).
3. **VPS:** contratar Hetzner CX22 (UE), firmar el DPA, apuntar el DNS de medeben.net,
   y pasarme la IP para desplegar (Caddy + Docker ya preparados).
4. **Play Console:** crear cuenta Personal (25 USD), verificar identidad, rellenar
   Data Safety + IARC (borradores listos), subir el `.aab` + assets, y **arrancar el
   test cerrado (12 testers × 14 días) cuanto antes** — es el único cuello de botella.

---

## 4. Riesgos / pendientes conocidos

- **Deploy a producción** aún no hecho esta sesión (bloqueado por permisos en sesiones
  previas; Flyway aplicará V6+V7 al desplegar). El `deploy/` está preparado.
- **C4 · cifrado en columna del `motivo`** sigue pendiente ("antes de escalar"). Por eso
  los textos legales NO afirman que se cifre en BD.
- **Ramas remotas viejas** de la QA ronda 2 (fix/api-robustez, fix/asistente-puesto,
  fix/colectiva-horas-extra, fix/dia-no-cuadra) siguen en origin; si sus PRs están
  cerradas, se pueden borrar. No las toqué (no son de esta sesión).
- Puede quedar un dev server de Vite (5173/5174) abierto de la sesión de QA; inofensivo,
  ciérralo cuando quieras.

## 5. Cómo retomar

Todo en `main`. Para desplegar: pasar la IP del VPS. Para Play: seguir
`medeben-play-checklist.md`. El plan de trabajo de código está agotado; lo que queda
es infraestructura y cuenta.
