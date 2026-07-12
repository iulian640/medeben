# HANDOFF — Lanzamiento MeDeben

> Escrito el 2026-07-12 (madrugada) por Claude Fable 5, antes de un `/clear`.
> Objetivo de Iulian: **sacar MeDeben mañana** (web + Play). Este documento es el
> plan completo para retomar en frío.

---

## 0. Cómo retomar (instrucciones para Claude tras el `/clear`)

1. Lee este fichero entero antes de tocar nada.
2. **No repitas el trabajo ya hecho** (sección 1: el build está verde, los tests
   pasan, la revisión legal documental ya existe en `docs/cumplimiento-lanzamiento.md`).
3. Ejecuta la **Fase 1** (auditoría) con el script ya escrito:
   `Workflow({ scriptPath: "C:\\Users\\iulia\\Documents\\tedeben\\.claude\\workflows\\auditoria-lanzamiento.mjs" })`
4. Con los hallazgos confirmados, ejecuta la Fase 2 (arreglos) y la Fase 3 (legal).
5. Al cerrar, skill `safe-harbor`.

Reglas de esta sesión que dio Iulian: usar Workflow; **Opus para lo importante,
Sonnet para lo básico, Fable solo para lo muy importante y como mucho 10 agentes
Fable**. Ese reparto ya está codificado en el script.

---

## 1. Estado verificado (a 2026-07-12, 03:00)

| Cosa | Estado |
|------|--------|
| Rama `main` | limpia, sincronizada con `origin/main` (último: merge PR #241) |
| Suite de tests backend (`mvn test`) | **VERDE** (exit 0) — comprobado hoy |
| Issues abiertas en GitHub | **ninguna** (QA ronda 2 cerró #229-#233) |
| Revisión legal documental | hecha: `docs/cumplimiento-lanzamiento.md` (mapa A/B/C) |
| Plantillas legales | existen pero **son borradores con `[CORCHETES]` sin rellenar**: `docs/legal/{aviso-legal,privacidad,terminos,disclaimers}.md` |
| Dominio | `medeben.net` comprado |
| VPS | **sin contratar** |
| Auditoría de código de seguridad/calidad | **NO hecha** ← esto es la Fase 1 |

Lo que **no** se ha hecho nunca: una auditoría adversarial del código con foco en
seguridad (authz/IDOR), corrección del dinero y fallos básicos de funcionamiento.
Eso es el grueso del trabajo de hoy.

---

## 2. Respuestas a las tres preguntas que hizo Iulian

### 2.1 «No tengo NIF, no estoy dado de alta — ¿qué datos necesito?»

Confusión de conceptos. **Sí tienes NIF.**

- **NIF de una persona física = tu número de DNI (o NIE si es de extranjería), con
  su letra.** No hay que darse de alta de nada para tenerlo: lo tienes desde que
  tienes DNI. Lo que no tienes es el **alta como autónomo** (RETA / modelo 036),
  que es una cosa distinta y **no hace falta para lanzar una app gratuita**.
- **Ser "responsable del tratamiento" del RGPD no requiere ser empresa ni
  autónomo.** Una persona física puede serlo perfectamente.

Datos que hacen falta según qué publiques:

| Documento | Qué exige | ¿Obligatorio si la app es gratis y sin donaciones? |
|-----------|-----------|--------------------------------------------------|
| Política de privacidad (RGPD art. 13) | Identidad del responsable + **un canal de contacto** | **SÍ, obligatoria.** Basta **nombre y apellidos + email**. El RGPD **no** te obliga a publicar tu domicilio. |
| Aviso legal (LSSI art. 10) | Nombre, NIF, domicilio, email | **NO**, solo si hay actividad económica (donaciones, publicidad, venta). Sin donaciones, **no aplica**. |

**Conclusión operativa:** si lanzas sin botón de donaciones, para mañana te basta
con **nombre + apellidos + un email de contacto**. Nada de NIF ni domicilio
público. Eso elimina el problema de golpe.

Lo único que hay que montar: **el email del dominio**. Necesitas que
`privacidad@medeben.net` y `contacto@medeben.net` lleguen a algún sitio. Lo más
barato es un **reenvío de correo** en el registrador del dominio (casi todos lo
dan gratis: alias → tu Gmail). Coste 0, 10 minutos. **Es un bloqueante real**: la
política de privacidad tiene que llevar un email que funcione de verdad.

### 2.2 «¿Puedo aceptar donaciones sin ser autónomo?»

**Respuesta honesta: es una pregunta de gestor, no mía**, y la respuesta correcta
depende de tu comunidad autónoma y de cuánto entre. Lo que sí puedo decirte con
seguridad es lo que te conviene **mañana**:

- Una donación pura (sin contraprestación) tributa en el **Impuesto de Sucesiones
  y Donaciones**, no en IRPF, y no exige alta de autónomo por sí sola.
- **Pero** si la donación va ligada a un producto que distribuyes y es recurrente,
  Hacienda puede recalificarla como **actividad económica** → alta censal (036) y,
  según ingresos y habitualidad, RETA. La frontera es difusa y la aplican caso a
  caso.
- Además, poner el botón activa la **LSSI art. 10** → aviso legal obligatorio con
  **NIF y domicilio públicos**. Justo lo que en 2.1 nos habíamos ahorrado.

**Recomendación clara: lanza mañana SIN donaciones.** No te cuesta nada aplazarlo,
te ahorra el aviso legal completo, el domicilio público y toda la incertidumbre
fiscal. Cuando la app tenga usuarios, hablas con un gestor (30-60 €) y lo añades
en una tarde. Aplazarlo es gratis; equivocarse no.

### 2.3 Hosting (VPS) — recomendación

**Hetzner Cloud, plan CX22** (2 vCPU, 4 GB RAM, 40 GB SSD, ~4-5 €/mes), ubicación
**Falkenstein o Núremberg (Alemania) o Helsinki (Finlandia)** — todo UE.

Por qué Hetzner y no otro:
- Es el más barato con diferencia para lo que necesitas (Spring Boot + Postgres
  en Docker caben de sobra en 4 GB).
- **DPA (contrato de encargado de tratamiento, art. 28 RGPD) gratis y a un clic**
  desde la consola (Legal → *Order processing agreement / Auftragsverarbeitung*).
  Eso cierra el punto **B7** del checklist de cumplimiento.
- Datacenters en la UE → **sin transferencias internacionales** que declarar.

Alternativa si prefieres Francia: **OVH** o **Scaleway** (también UE, también con
DPA). No cambia nada del plan.

Pasos concretos (los hace Iulian, son 20 min):
1. Crear cuenta en Hetzner Cloud.
2. Crear servidor **CX22**, imagen Ubuntu 24.04 LTS, región Falkenstein.
3. En la consola: aceptar/firmar el **DPA** y guardar el PDF.
4. Apuntar el DNS de `medeben.net` (registro A) a la IP del servidor.
5. Pasarme la IP → yo despliego (Caddy + Docker ya están preparados en `deploy/`).

Para los textos legales, el proveedor queda como:
> *Hetzner Online GmbH, con servidores en Alemania (Unión Europea), con contrato
> de encargado de tratamiento (art. 28 RGPD).*

---

## 3. AVISO IMPORTANTE — «Subir a Play mañana» probablemente no es posible

Google exige, para las **cuentas de desarrollador personales creadas después de
noviembre de 2023**, un **test cerrado con un mínimo de testers (≈12) suscritos de
forma continua durante 14 días** antes de poder siquiera solicitar el acceso a
producción. Si tu cuenta de Play es personal y nueva, **la app no puede estar en
producción mañana: lo más rápido posible es empezar mañana el test cerrado.**

> ⚠️ **Verificar antes de dar esto por bueno** — la política de Google cambia a
> menudo. Hay que mirarlo en la Play Console real de Iulian (o en
> https://support.google.com/googleplay/android-developer/answer/14151465).
> No lo he podido comprobar porque no tengo acceso a su cuenta.

**Consecuencia para el plan:** el lanzamiento de mañana es **la web
(medeben.net + PWA instalable)**. Play es un carril paralelo que se *arranca*
mañana (subir el AAB a test cerrado + Data Safety) y llega a producción semanas
después. Conviene que Iulian lo confirme cuanto antes, porque cambia qué es
"lanzar".

---

## 4. Preguntas que siguen abiertas para Iulian

1. **Email del dominio**: ¿el registrador de `medeben.net` da reenvío de correo?
   ¿A qué buzón quieres que lleguen `privacidad@` y `contacto@`? (bloqueante)
2. **Play Console**: ¿la cuenta de desarrollador ya está creada y pagada (25 $)?
   ¿Es personal o de organización? ¿De qué fecha? (determina lo de la sección 3)
3. **Nombre público**: ¿firmas la política de privacidad como *Iulian Timofei*?
4. ¿Confirmas **lanzar sin donaciones**? (mi recomendación)
5. ¿Contratas **Hetzner** o prefieres otro?

---

## 5. Plan de trabajo

### Fase 1 — Auditoría integral (workflow, ya escrito)

Script: `.claude/workflows/auditoria-lanzamiento.mjs`
Invocación: `Workflow({ scriptPath: "C:\\Users\\iulia\\Documents\\tedeben\\.claude\\workflows\\auditoria-lanzamiento.mjs" })`

12 auditores en paralelo, cada hallazgo verificado por 3 escépticos independientes
(lentes distintas: ¿es real?, ¿se reproduce?, ¿bloquea el lanzamiento?). Solo
sobrevive lo que 2 de 3 confirman. Reparto de modelos tal como pidió Iulian:

| Auditor | Modelo | Foco |
|---------|--------|------|
| authz-idor | **Fable** | ¿Todo endpoint filtra por el usuario autenticado? Fugas entre usuarios |
| auth-sesiones | **Fable** | JWT, rotación de refresh, revocación, detección de robo, secretos |
| dinero-valor-hora | **Fable** | Motor de cálculo: valor hora, horas extra, recargos |
| dinero-topes-informe | **Fable** | Topes anuales, SMI, resumen mensual/anual, cifras del PDF |
| rgpd-codigo | **Fable** | Dato de salud (`motivo`), logs con PII, caché del PDF, borrado real |
| bugs-basicos | Opus | Fallos silenciosos, NPE, errores no manejados, casos límite |
| datos-integridad | Opus | JPA, transacciones, concurrencia, migraciones Flyway V1-V7 |
| frontend-vue | Opus | Reactividad, XSS, manejo del token, offline/PWA |
| deploy-prod | Opus | Caddyfile, nginx, docker-compose, `application-prod.yml`, secretos, backups |
| deps-cve | Sonnet | Dependencias con CVE conocido / desactualizadas |
| tests-huecos | Sonnet | Huecos de cobertura en lo crítico |
| ux-errores | Sonnet | Mensajes de error al usuario, castellano, estados vacíos |

Total Fable: **5 auditores + 1 sintetizador = 6** (por debajo del límite de 10).
La verificación adversarial va con Opus (crítico/alto) y Sonnet (el resto).

Salida: lista de hallazgos confirmados, ordenados, separando **BLOQUEA EL
LANZAMIENTO** de lo demás.

### Fase 2 — Arreglos

Pipeline sobre los hallazgos bloqueantes, cada uno en su worktree para que no
choquen. TDD (test en rojo primero — es norma del proyecto). PR por hallazgo.
Modelo: Opus para seguridad/dinero, Sonnet para lo mecánico.

### Fase 3 — Legal y lanzamiento

Ya con las respuestas de la sección 4:

1. Rellenar los 4 documentos de `docs/legal/` (quitar `[CORCHETES]`).
2. **Publicarlos como páginas reales** en la web: `/privacidad`, `/aviso-legal`,
   `/terminos`, y **`/borrar-cuenta`** (esta la exige Google Play y hoy no existe).
3. **B2 del checklist**: enlace visible a la privacidad **en la pantalla de
   registro**, con la info del art. 13. Hoy el registro solo pide email+contraseña.
4. **C5**: disclaimers en pantalla ("orientativo", "no es asesoramiento jurídico")
   en la cifra de "te deben X €" y en el PDF.
5. **B5 (RAT)** y **B6 (EIPD)**: documentos internos. Obligatorios por el dato de
   salud. Se pueden redactar hoy.
6. **B4**: cuestionario *Data Safety* de Play Console, coherente con la privacidad
   (declarar email + dato de salud + cifrado en tránsito + borrado).
7. Desplegar en el VPS, TLS con Caddy, secretos frescos, cron del backup.

**Ojo con C4**: el `motivo` de ausencia **no está cifrado en columna**. Por tanto
la privacidad **no puede decir que se cifra en base de datos** (sería falso).
Está marcado en `docs/legal/disclaimers.md`; hay que respetarlo al rellenar.

---

## 6. Riesgos conocidos que arrastramos

- **Deploy a producción bloqueado por permisos** en sesiones anteriores; Flyway
  tendrá que aplicar V6+V7 al desplegar.
- **Keystore de firma** en `C:\Users\iulia\Documents\medeben-release` — **sin
  backup**. Si se pierde, no se puede volver a publicar una actualización en Play
  NUNCA. Copiarlo fuera del portátil hoy mismo.
- Tarea programada del backup requiere admin.
