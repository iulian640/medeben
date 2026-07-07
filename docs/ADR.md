# ADR — TeDeben (nombre provisional)

**Fecha:** 2026-07-07
**Autor de las decisiones:** Iulian (producto) + Claude Fable 5 (documentación y verificaciones)
**Estado del proyecto:** idea completamente definida. **Cero código escrito** — decisión explícita de Iulian: primero construir la idea, luego picar.
**Prioridad:** por detrás de IulianLounge (entrega 13-oct) y vue-multitool (ejercicio puntuado del bootcamp).

---

## Qué es

App **gratuita** del lado del **trabajador** de hostelería en España: registra tu horario, te persigue para fichar, guarda tus cuadrantes como prueba, calcula lo que te deben según tu convenio ("te deben X€ este mes") y te da las herramientas para reclamarlo.

**El hueco:** todo lo que existe (Factorial, Sesame, Combo, Skello) es B2B del lado de la empresa. Los laboralistas recomiendan justo este registro propio como prueba; desde el RD-ley 8/2019 la carga de la prueba favorece al trabajador. Nace del conocimiento de dominio de Iulian como excocinero.

---

## Decisiones tomadas

### D1 — Gratis para el trabajador; dinero solo "ético" (matizada 2026-07-07)
- **Línea roja de Iulian:** el trabajador de hostelería (que "ya está castigado") nunca paga y sus datos nunca se venden. Ninguna funcionalidad capada por dinero para él. Objetivo primero: ganar usuarios.
- **Pero no es amor al arte puro:** si la app funciona, Iulian quiere sacar dinero — "ético", nunca en detrimento del trabajador. Vías compatibles identificadas:
  - Abogados laboralistas pagan por clientes que el usuario solicita explícitamente ("quiero que me contacte un laboralista") — el trabajador recibe ayuda gratis, el abogado recibe clientes con las pruebas ordenadas, iniciativa siempre del usuario.
  - Patrocinio transparente de sindicatos (les interesa estar en la app del sector).
  - Donaciones (D16).
- **Prohibido para siempre:** paywall al trabajador, venta de datos, publicidad invasiva. Frase de Iulian: "ni sus datos ni sus herramientas" — lo que hoy es gratis no se convertirá en de pago jamás.
- Descarta el freemium que se barajó al principio. v1 sin pasarelas de pago; el único coste es hosting.

### D2 — Stack: Java/Spring (backend) + Vue 3 (frontend)
- Es el stack del bootcamp de Iulian: podrá leerla, mantenerla y enseñarla como portfolio.
- Descartada la alternativa Vue + Supabase-sin-backend (más simple pero sin Java que enseñar).

### D3 — Hosting objetivo: 0€/mes
- Frontend Vue → Vercel / Netlify / Cloudflare Pages (gratis).
- Backend Spring → Render free tier (gratis; se duerme tras 15 min sin uso, despierta en ~1 min — aceptable para validar).
- BD → Neon o Supabase Postgres (gratis hasta 0,5-1 GB).
- Fotos de cuadrantes → Supabase Storage (1 GB gratis).
- Escalada futura si molesta el sleep: VPS Hetzner ~4,5€/mes. Dominio ~8-10€/año, opcional.

### D4 — Todos los convenios, elegidos por zona
- Requisito de Iulian: la app tendrá **todos** los convenios de hostelería de España (~50, provinciales/autonómicos). El usuario elige dónde trabaja y se le aplica el suyo.
- Además: **visor del convenio** dentro de la app — el usuario puede consultar el convenio de su zona.
- ⚠️ PENDIENTE de diseñar cómo (ver "Lo que falta"): es el hueso técnico del proyecto — los convenios cambian cada año, tienen tablas por categoría y nivel.

### D5 — Flujo core: la app te persigue, no fichas tú (idea de Iulian)
Resuelve el problema nº1 de toda app de fichaje: que nadie se acuerda de fichar.
1. Subes tu horario **semanal o mensual**, o dejas uno fijo predefinido.
2. A la hora de entrada teórica, notificación con opciones:
   - **Entré a mi hora**
   - **Entré tarde**
   - **No fui a trabajar** (con motivo — enfermedad, etc. — queda registrado como ausencia; las ausencias justificadas también son historial que protege)
3. A la hora de salida teórica, notificación "tu turno ha acabado, ficha la salida" con opciones:
   - **Salí a mi hora** (un toque)
   - **Salgo ahora** (hora real = momento de pulsar — aquí nacen las horas extras)
   - **Poner hora manualmente** (corrección a posteriori)
4. Delta entre hora teórica y real = horas extras calculadas solas. El usuario no hace cuentas jamás.

### D6 — Cuadrantes: actualizables y con historial
- La foto del cuadrante (lo que publicó la empresa) es la **prueba**; el horario tecleado en la app es lo que mueve las notificaciones. Son dos cosas que se complementan.
- Opción **"actualizar cuadrante"**: te cambian el turno → actualizas el horario. El cuadrante viejo NO se borra: queda en historial con su fecha. Prueba de *cuándo* te lo cambiaron (los cambios sin preaviso son otra cosa reclamable).

### D7 — Nombre: TeDeben (pendiente de "dormirlo 24h")
- Proceso hecho con la skill de naming, disponibilidad **verificada con herramientas** el 2026-07-07:
  - `tedeben.es`, `tedeben.app`, `tedeben.com` → libres (sin DNS). GitHub `tedeben` → libre. Sin marca ni app conflictiva encontrada.
- Historia: el nombre es literalmente el mensaje de la app — "Te deben 340€ este mes".
- Taglines candidatas: "Las horas que trabajas, cobradas." / "Apunta lo que curras. Reclama lo que te deben."
- Descartados: **MeDeben** (conflicto directo con medeben.es, base de datos de morosos española), **MiCuadrante**, **Doblete**, **LaCuenta/LaComanda** (dominios/GitHub ocupados), **LaLibreta** (dominios ocupados — pero la metáfora "tu libreta digital" puede vivir dentro de la app), **TusHoras** (2º finalista, tushoras.app libre, sin chispa).
- Acción pendiente: si tras dormirlo sigue gustando, registrar `tedeben.es` (~8€/año) — único gasto real del proyecto.

### D8 — Features aprobadas (Iulian dijo sí a todas, 2026-07-07)
Del mismo palo que las horas extras (la app ya conoce el horario):
- **Festivos y libres no disfrutados** — "este mes trabajaste 2 festivos y te quitaron 1 libre".
- **Nocturnidad** — plus horas 22h-6h según convenio; sale solo de las horas fichadas.
- **Contrato parcial trampa** — horas de contrato (perfil) vs horas reales: evidencia el fraude nº1 del sector (contrato de 20h trabajando 40).

Comprobadores:
- **Comprobador de nómina** — foto de la nómina vs tabla del convenio para tu categoría.
- **Comprobador de categoría profesional** — te tienen de ayudante haciendo trabajo de cocinero.

Momentos críticos:
- **Modo "me han despedido"** — checklist de las primeras 48h + cuenta atrás de los 20 días hábiles para demandar.
- **Calculadora de finiquito** — vacaciones no disfrutadas + pagas prorrateadas + días del mes.

Transversal:
- **Multi-idioma** — rumano, árabe, inglés... mucha gente de cocina no domina el castellano. Nadie tiene una app de derechos laborales en tu idioma. Iulian mismo es el caso de uso.
- **Exportar informe PDF** como evidencia para SMAC/abogado (de la idea original).

### D9 — Sección Recursos (por zona del usuario)
- v1: **sindicatos** (federaciones de hostelería CCOO, UGT...), **Inspección de Trabajo**, **SMAC de cada CCAA**, **guías "cómo reclamar paso a paso"** + el visor del convenio (D4). Todo dato público y estable, precargable.
- v2: **directorio de abogados laboralistas por zona** — requiere curación manual, llega después.

### D10 — Herramienta de denuncia a Inspección (idea de Iulian): ambas vías
- **Denuncia formal ITSS**: no anónima pero **confidencial** — la Inspección tiene prohibido revelar la identidad a la empresa. Mucha gente no lo sabe.
- **Buzón contra el fraude laboral**: 100% anónimo, online, pero no vinculante (es pista, no denuncia).
- La app explica la diferencia en cristiano + **asistente** que prepara el texto de la denuncia con los datos que ya tiene (empresa, registros de horas, cuadrantes con fecha).

### D11 — Plano legal (mapa de riesgos, no dictamen — validar con laboralista)
La app en sí es legal: registrar tus propias horas es lo que recomiendan Inspección y los laboralistas. Los flancos, por riesgo:
1. **RGPD — el mayor.** Las fotos de cuadrante llevan nombres/turnos de compañeros (datos de terceros) en el servidor de Iulian. Un empresario no demanda: **denuncia gratis ante la AEPD**. Mitigación desde el día 1: política de privacidad, hosting en la UE, minimización de datos, base jurídica de defensa de derechos propios del usuario.
2. **Responsabilidad por cálculo erróneo.** Disclaimer en pantalla y en términos: "cálculo orientativo según tablas del convenio X (año Y), verifica con un profesional antes de reclamar".
3. **Asesoramiento legal sin ser abogado.** Información ≠ asesoramiento; disclaimer "esto no es asesoramiento jurídico" en guías y asistente de denuncias.
4. **LSSI.** Aviso legal con identificación del responsable, obligatorio aunque la app sea gratis.
5. **Difamación — riesgo esquivado por diseño.** Todo lo del usuario es privado, para su propia reclamación. **NUNCA** meter reviews públicas de empresas ni listas negras: eso convierte el proyecto en demandas por difamación.

Plan: 4 documentos (aviso legal, privacidad, términos de uso, disclaimers) + que un laboralista los revise cuando la app los atraiga de forma natural (coste ~0).

---

### D12 — Alcance v1 (cerrado 2026-07-07)
**v1 = el corazón y nada más:**
- Perfil (zona, convenio, categoría, horas de contrato)
- Horario semanal/mensual + fijo predefinido
- Notificaciones de entrada/salida con sus opciones (D5)
- Foto de cuadrante con historial (D6)
- "Te deben X€" (horas extras)
- Informe PDF
- **Cumplimiento legal desde el día 1** (D11): los 4 documentos (aviso legal, privacidad, términos, disclaimers), hosting UE, borrado de cuenta y export de datos (derechos RGPD)

**Cola v2+:** nocturnidad, festivos/libres no disfrutados, comprobadores de nómina y categoría, finiquito, modo despido, recursos por zona, asistente de denuncias, multi-idioma, directorio de abogados. Intención de Iulian: v2 lo más rápido posible tras la v1.

### D13 — Decisiones técnicas (aprobadas 2026-07-07)
1. **Notificaciones locales, no push del servidor.** El backend en Render free se duerme → no puede enviar a horas exactas. La app programa las alarmas en el propio móvil a partir del horario. Funciona sin cobertura y con el servidor dormido. Consecuencia: la app móvil se hace con **Capacitor** (las notificaciones programadas no son fiables en web pura).
2. **Offline primero.** Fichar funciona sin internet (móvil en taquilla, sótanos sin cobertura) y sincroniza después.
3. **Los datos son la prueba legal del usuario.** Backups automáticos de BD desde el día 1. Cada foto/fichaje recibe sello de fecha del servidor + hash del archivo (integridad demostrable, valor probatorio).
4. **Auth simple:** email + contraseña, Spring Security + JWT. Sin login social en v1.

### D14 — Plataformas: una app Vue, tres salidas (2026-07-07)
Mismo código Vue → web (PWA) + Android + iOS vía **Capacitor**. Orden:
1. **Web (PWA):** gratis, para consultar/subir cuadrantes desde PC/informes. No es el vehículo principal (notificaciones programadas poco fiables en web).
2. **Android (Play Store) — la app de verdad** (notificaciones locales; el usuario de cocina es mayoritariamente Android). Requisitos: cuenta 25$ (pago único), verificación DNI, URL de política de privacidad (ya cubierta por D11), formulario de seguridad de datos, y para cuentas personales nuevas **prueba cerrada con ~20 testers 14 días** → los excompañeros de Iulian son los testers: validación + requisito de Google a la vez.
3. **iOS (App Store):** cuando haya tracción — 99$/año recurrentes + necesita Mac (o build en la nube) + revisión más estricta. Se pospone por coste, no por técnica.

### D15 — UX de mínima fricción: "el usuario cansado que no hace nada, también ficha"
Contexto de Iulian: la gente de hostelería sale reventada, no sigue costumbres como fichar. Ley de diseño de toda la app. Medidas:
1. **Fichar desde la notificación** — botones de acción en la propia notificación ("Salí a mi hora" / "Salgo ahora"), sin abrir la app. Android lo soporta de serie.
2. **El silencio ficha por ti** — si no contesta, se asume el horario teórico, marcado como "auto" (corregible; la marca mantiene la honestidad probatoria). El peor caso nunca es "no hay datos".
3. **Repaso del día libre** — vista semanal "3 confirmados, 2 auto, ¿algo que corregir?". Red de seguridad de 1 y 2.
4. **Widget "SALGO YA"** en pantalla de inicio, un toque.
5. **(v3) Geofencing** — fichaje automático por ubicación; opt-in explícito, RGPD serio, batería. El final del camino.
6. **Modo "ponerme al día"** (idea de Iulian) — reconstruir meses pasados de golpe: eliges rango → subes fotos de cuadrantes ya hechas (los metadatos EXIF conservan la fecha de captura = prueba de existencia) → aplicas patrón de horario al rango → excepciones en lote sobre calendario ("los viernes salí ~2h tarde"). Límite honesto: la reclamación de cantidades prescribe a 1 año → reconstruye máx. 12 meses y lo explica. Cada registro lleva su origen: **confirmado / auto / reconstruido** (honestidad probatoria).
   Sistema de 3 niveles: fichas en el momento (mejor) / no haces nada, auto-asume (red) / llegas tarde, reconstruyes (puerta de entrada — la mayoría descargará la app cuando ya le deban meses).

### D16 — Donaciones para financiar iOS (decisión de Iulian 2026-07-07)
- Botón de donaciones con objetivo transparente: "99$/año para que TeDeben exista en iPhone". NO rompe D1: donar no desbloquea nada, la app sigue entera y gratis.
- **El botón vive SOLO en la web** (Ko-fi / Buy Me a Coffee, ~95% llega, sin papeleo). En las apps de tienda: **nada de donaciones, ni enlaces discretos** — decisión explícita de Iulian: todo estrictamente conforme a las normas de cada tienda. Si algún día se quieren donaciones in-app, se hace por el sistema de pago oficial de la tienda aceptando su comisión (15-30%).
- Los 25$ del Play Store los paga Iulian cuando la app esté lista para salir.
- Nota fiscal futura: si las donaciones dejan de ser calderilla, en España tributan (ISD por CCAA) → mirar con gestor. Con cafés sueltos, irrelevante.

### D18 — La filosofía visible en la app: "está de tu parte, no te vendo" (decisión de Iulian)
1. **Manifiesto** a un toque desde el menú, corto y en cristiano: nunca pagarás / **nunca venderé ni tus datos ni tus herramientas** (lo que hoy es gratis no se convertirá en de pago jamás) / tus datos son tuyos (export/borrado) / si gano dinero será de abogados y sindicatos, nunca de ti.
2. **"Hecha por un excocinero"** — el backstory de Iulian como credibilidad, en pantalla de inicio y ficha de tienda.
3. **Código público como prueba** (si AGPL, D17): "no me creas: compruébalo".
4. **Copy transparente en momentos sensibles:** "esta foto solo la ves tú", "solo te pido email". La confianza vive en los microtextos.

### D17 — Copyright y propiedad (consulta 2026-07-07)
- **Copyright: automático**, nace al crear (España/UE), sin registro ni coste. El historial de git en GitHub es la prueba de autoría y fecha. Registro de la Propiedad Intelectual: opcional, innecesario.
- **Las ideas no se protegen** — solo la expresión (código, textos). Nadie puede impedir apps competidoras; la defensa es ejecutar mejor.
- **Marca "TeDeben" en la OEPM** (~150€, 10 años): registrar cuando la app tenga usuarios y el nombre valga algo. No corre prisa; dominio + GitHub dan prioridad de facto.
- **Pendiente al crear el repo:** elegir licencia si el código es público. Candidata: AGPL (impide que alguien lo convierta en producto cerrado de pago — encaja con el espíritu "por amor al arte").

---

### D19 — Reparto de trabajo (cerrado 2026-07-07)
- **Convenios:** los investigan **juntos** (Iulian + Claude). Primer pendiente al arrancar. Ideas de partida: modelo de datos genérico (convenio → año → categoría → salario base / plus nocturnidad / precio hora extra), carga incremental (1-2 provincias reales de piloto + modo configurable a mano como respaldo), fuente oficial (BOE / boletines provinciales).
- **Código: lo escribe Claude, con supervisión de Iulian.** NO es proyecto didáctico (a diferencia del bootcamp) — no aplica el modo enseñanza sino el ciclo normal: git completo por Claude (ramas, conventional commits, PR, merge, borrar ramas), tests, code review.
- **Validación: la hace Iulian** — enseñársela a 3-4 excompañeros de cocina ("¿la usarías de verdad?"), que además serán los ~20 testers que exige Play Store (D14): validación y requisito de Google a la vez.

---

## Lo que falta (en orden, para la próxima sesión)

1. **Investigar convenios juntos** — el hueso técnico: cuántos son exactamente, dónde se publican, qué estructura tienen las tablas, cuál usar de piloto.
2. **Registrar `tedeben.es`** si el nombre sobrevive a la almohada (~8€/año, lo hace Iulian).
3. **Crear repo GitHub** (licencia AGPL pendiente de confirmar, D17) y arrancar la v1 (D12): Claude codea → Iulian supervisa y valida.
4. **Mirar la competencia** antes/durante la v1 (las apps de registro existentes, todas B2B lado-empresa).

## Notas para la próxima sesión de Claude

- El detalle vivo está en la memoria automática: `project_idea_horas_extras.md`. Este ADR es la copia completa y durable en los Documentos de Iulian.
- **Tooling (revisado 2026-07-07):** skills de Spring/Vue/Postgres/TDD/security + agentes revisores cubren el stack. Agente `data-privacy-officer` restaurado del archivo para el trabajo RGPD (D11). En `agents-archive/` queda `marketing-app-store-optimizer` para la ficha de Play Store cuando toque. Gaps sin skill: Capacitor y offline-first → usar `documentation-lookup` (Context7) con docs oficiales.
- **Modo de trabajo acordado:** Iulian verifica convenios con ayuda de Claude mientras un subagente construye la app en paralelo (él lo ha pedido explícitamente — spawnearlo cuando haya repo).
- **Convenios (investigado 2026-07-07):** el ALEH VI (BOE-A-2023-6344) da la clasificación profesional común nacional; ~50 convenios provinciales/autonómicos solo cambian los números; tablas en boletines provinciales (PDF); REGCON sin API; los textos oficiales no tienen copyright (art. 13 LPI) → transcribir es legal. Arquitectura: tablas como ficheros de datos en el repo (mantenimiento comunitario vía PR), cada tabla con su vigencia visible, + modo configurable de respaldo para provincias sin transcribir.
- **Orden de carga de convenios (decisión de Iulian 2026-07-07): por población, de más a menos.** Primera tanda: 1) Madrid (autonómico), 2) Barcelona → convenio de Cataluña interprovincial (incluye Girona, Tarragona y Lleida gratis), 3) Valencia, 4) Alicante, 5) Sevilla. = 8 provincias y ~20M de habitantes cubiertos de salida. Iulian verifica las tablas transcritas (con ayuda de Claude) mientras un subagente codea.
- Git/fontanería lo hace Claude sin preguntar (ver CLAUDE.md global). Todavía **no hay repo** — crearlo cuando se decida arrancar.
- Iulian: castellano de España, tuteo, directo, mensajes cortos, él marca el ritmo. No cerrar cada mensaje con pregunta.
