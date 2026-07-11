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
- **Detección de pago en negro / plus encubierto** (idea de Iulian 2026-07-07): en hostelería las horas extra, si se pagan, suelen ir en negro o disfrazadas de "plus voluntario" para que no consten (no cotizan, no cuentan para paro/jubilación). El comprobador de nómina cruza las horas extra que la app ha registrado con lo que aparece en la nómina y avisa: "cobras un 'plus' de X€ que probablemente son tus horas extra sin nombrar — esto te perjudica". Educar sobre por qué el negro perjudica al trabajador.

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

### D22 — Los cálculos saltan al fichar, no son herramientas aparte (Iulian 2026-07-07)
- Nocturnidad, horas extra, festivos, detección de plus encubierto... NO son pantallas separadas en un menú: se calculan y se muestran **en el momento en que el usuario registra sus horas**, en caliente. Al cerrar el turno la app avisa ahí mismo ("este turno: 2 h nocturnas +25%, 1 h extra; ojo si te la pagan como 'plus'").
- Principio: el trabajador cansado no va a bucear en menús. El valor aparece solo, pegado a la acción que ya hace (fichar). Las vistas de resumen ("te deben X€ este mes") son agregados de esto, no la fuente.
- **Aviso de tope legal en caliente (Iulian 2026-07-07):** la app lleva la cuenta de horas extra acumuladas en el año; cuando al fichar vayas a superar el tope, te avisa. El tope es **80 h/año O lo que fije el convenio de cada zona** (el 80 es el mínimo del ET; cada convenio puede tener el suyo) → campo `topeHorasExtraAnual` por convenio.

### D32 — Norma de currency: SIEMPRE la última publicación (Iulian 2026-07-07)
- Antes de transcribir un convenio, buscar la publicación MÁS RECIENTE de sus tablas. Un dato viejo es un dato erróneo, y "si damos información errónea no valemos nada" (Iulian).
- Cuidado con "provisional vs definitiva": las tablas de un año salen primero provisionales (con cláusula IPC) y luego definitivas. Usar siempre las definitivas del año en curso.
- Caso real detectado: el 1er PDF de Málaga (BOP 90, may-2025) traía 2025 PROVISIONALES; estando en 2026 hay que usar el BOP 70 (abr-2026) con las 2025 DEFINITIVAS. Y Alicante: transcrita la tabla 2025 cuando ya existe la 2026.
- La app debe mostrar la vigencia de cada tabla y avisar si podría haber una más nueva. `convenios/ESTADO.md` lleva el control de currency por convenio.

### D31 — Jerarquía de normas y suelo europeo (Iulian preguntó por convenios europeos, 2026-07-07)
- Aclaración: NO existen "convenios colectivos europeos" que fijen salarios. Lo que está por encima de lo nacional es la **legislación de la UE** (directivas), que fija mínimos irrebasables. Jerarquía (VERIFICADO en la red):
  1. **Derecho UE** — Directiva 2003/88/CE de tiempo de trabajo: máx **48 h/semana** (incluidas extra), descanso diario mín **11 h**/24 h, descanso semanal 24 h + 11 h, **4 semanas** de vacaciones. Suelo absoluto.
  2. **Ley española (ET)** — suele mejorar la UE (p. ej. 12 h de descanso entre jornadas vs 11 h UE).
  3. **Convenio estatal/sectorial** → **provincial/autonómico** (tablas) → **contrato individual**.
  - Principio de **norma más favorable** al trabajador.
- **Uso en la app:** codificar los mínimos UE/ET como *backstop* absoluto. Si un convenio o contrato baja de ahí, la app lo marca como ilegal. Ej. detectado: Cataluña reduce el descanso entre jornadas a 10 h — la UE lo permite en hostelería (turnos/jornada partida) SOLO con **descanso compensatorio equivalente**. La app avisa: "te han dado 10 h entre turnos; te deben recuperar esa hora en descanso". (Confirmar con UGT.)

### D29 — "Derechos que no sabías que tienes": dinero oculto del convenio (hallazgo al transcribir, 2026-07-07)
- Repasando convenios aparece MUCHO dinero/derechos que el trabajador no reclama porque no sabe que existen. Feature potente (enganche + valor real + dinero de verdad):
  - **Premios en metálico:** gratificación por matrimonio (Madrid hospedaje: 1.033 €), premio de natalidad (784 €).
  - **Seguro de vida/invalidez obligatorio** que la empresa DEBE contratar (12.000-25.000 €). La app avisa de que existe y sugiere comprobar que esté contratado.
  - **Pluses menores** que no se pagan: transporte, ropa de trabajo, manutención/alojamiento en especie.
- Feature: sección **"Derechos que no sabías que tienes"** por convenio. El usuario mete su situación (me caso, tengo un hijo, trabajo de noche...) y la app le lista lo que le corresponde y cómo pedirlo. Complementa el "te deben X€" (que era solo horas extra) con TODO el dinero adeudado.
- Datos: cada JSON de convenio debe capturar estos conceptos (premios, seguros, pluses), no solo las tablas salariales.

### D30 — Contador de derechos de TIEMPO (hallazgo al transcribir, 2026-07-07)
- Además del dinero, hay derechos de tiempo que se vulneran mucho y la app puede vigilar con los fichajes que ya tiene:
  - **Descanso entre jornadas:** mínimo 12 h (ET); algunos convenios lo bajan (Cataluña a 10 h con condiciones). La app avisa si cierras y entras con menos margen del legal.
  - **Vacaciones:** varían por convenio (Cataluña 31 días, otros 30). Contador de vacaciones que te corresponden/debes.
  - **Jornada partida:** algunos convenios la compensan.
- Campos por convenio: `descansoEntreJornadasHoras`, `vacacionesDias`, reglas de jornada partida.

### D38 — Horario y fichajes: la libreta sellada (Iulian + Claude, 2026-07-08)
Concreta D5 (la app te persigue) y D6 (cuadrantes con historial) para la fase de fichajes. Decisiones de producto cerradas con Iulian:

- **Estructura del día:** libre, turno seguido (1 tramo) o partido (2 tramos, máximo). Cubre la realidad de hostelería sin complicar pantalla ni notificaciones.
- **Estructura del horario:** semana tipo que se repite sola + ediciones por semana concreta cuando te cambian el turno. Cada versión editada queda en historial con fecha (prueba de cambios de última hora, D6).
- **Entrada manual primero:** la foto del cuadrante (guardada con fecha) llega en una iteración posterior, junto con su análisis RGPD (salen compañeros).
- **Nada se auto-asume:** si el usuario no confirma un día, NO se guarda como trabajado. Mismo principio que los convenios: dato ausente > dato inventado. Un registro que se auto-rellena es fácil de atacar en juicio y contamina toda la libreta.
- **Ventana de confirmación de 14 días (idea de Iulian):** cada día queda "pendiente" hasta que el usuario lo confirma o reconstruye. Dentro de la ventana se puede corregir, guardando historial. A los 14 días el día se **sella**.
- **Rectificación tardía (idea de Iulian):** después del sello se puede rectificar, pero la rectificación va a un **registro propio** con su sello de fecha; el valor original sellado nunca se altera y sigue visible. En el informe PDF aparecen ambos, separados. Modelo contable: no se borra, se rectifica con apunte nuevo. En la UI lleva fricción y aviso explícito.
- **Origen de cada registro** (jerarquía probatoria): `confirmado` (fichado al momento, sello de hora del servidor — lo que más vale), `reconstruido` (confirmado dentro de la ventana, días después), `rectificacion_tardia` (tras el sello — vale poco, pero es honesto). Lo nunca confirmado = hueco permanente; un diario real tiene huecos, un diario perfecto canta.
- **Por qué (razón probatoria):** el sello de hora responde a "¿cuándo lo apuntó?" (contemporaneidad) y el sellado a 14 días responde a "¿pudo manipularlo cuando ya olía a juicio?" (consistencia). Las dos preguntas que un juez le haría al registro.
- **Modelo interno: diario de eventos append-only.** Nunca se edita ni borra nada; fichajes, correcciones, sellados y rectificaciones son apuntes nuevos con sello de servidor. El estado del día se deriva del diario.
- **Comunicación al usuario (obligatoria, Iulian: "hay que explicárselo muy bien"):** onboarding de la libreta (fichar al momento vale más / 14 días y se sella / los huecos son normales y dan credibilidad), microcopy de sellado al fichar ("✓ sellado a las 23:47"), contador de cierre ("el martes 12 se sella en 3 días"), y entrada del feed (D27) explicando por qué una libreta con huecos vale más que una perfecta.
- **Notificaciones: Capacitor entra ya en esta fase** (decisión de Iulian): notificaciones locales reales en Android en cuanto la mecánica esté; implica instalar Android SDK.
- **RGPD del motivo de ausencia (security-review 2026-07-08):** el `motivo` es texto libre que puede contener datos de salud ("enfermo") = **categoría especial, art. 9 RGPD**; base de tratamiento: defensa de derechos (art. 9.2.f). Reglas: solo existe en las ausencias (minimización por diseño, art. 25 — rechazado en cualquier otro tipo de apunte), NUNCA se loguea, sin toString() que lo incluya, y en el frontend jamás con v-html. A medio plazo (antes de escalar usuarios): valorar cifrado a nivel de columna.
- **Derivación del día por tramos (auditoría integral 2026-07-08, PR #136):** el estado del día se deriva emparejando los apuntes en orden — una ENTRADA abre tramo (máximo 2, la regla del día partido), una SALIDA cierra el abierto; dos apuntes seguidos del mismo tipo = corrección del anterior (gana el último); la AUSENCIA sigue siendo frontera que invalida lo anterior. Las correcciones se asignan **por posición** (el API no tiene id de tramo), así que para que un apunte mal dirigido nunca fabrique horas fantasma hay un **techo de cordura de 16 h por tramo**: si se supera, los minutos del día quedan "sin calcular" en vez de mentir. `minutosTrabajados` no va ligado al estado (un día EN CURSO con un tramo ya cerrado informa esos minutos). Decisiones abiertas: id de tramo/apunte en el API para correcciones dirigidas, y si el techo debe ser 16 o 18 h.
- **Día que NO CUADRA (issue #230, 2026-07-11):** cuando la lectura derivada se contradice — tramos que se pisan en el reloj del día, una entrada abierta dentro de un tramo cerrado (la fusión del turno partido reconstruido en desorden), o salidas huérfanas que ninguna lectura recoge — el día pasa al estado **NO_CUADRA**: sin tramos ni total (la lectura que había engaña), excluido de los agregados, y **visible** en la libreta (día y semana), en el resumen mensual (aviso con fechas, el primero de la lista) y en los informes PDF (etiqueta en el diario, fila en el resumen, agregado en el histórico anual). Principio de la issue: no se adivina la intención del usuario (eso pediría el id de tramo, decisión abierta arriba) — solo se deja de perder horas en silencio; ni un total plausible pero falso ni un cero mudo. Una huérfana queda "explicada" (no alarma) si algún tramo termina exactamente a su misma hora; y una salida corregida (typo re-tecleado, o un duplicado idéntico) no envenena un día que por lo demás cierra limpio: la última lectura gana (D38) y las correcciones superadas no cuentan (regresión cazada en la review de la PR). **Recuperación (matiz de la review):** muchos NO_CUADRA se resuelven re-apuntando el turno en orden, pero el caso de **tramos FUNDIDOS** (una entrada dentro de un tramo ya cerrado, con el cupo de 2 tramos lleno) NO se puede deshacer solo con apuntes —el slot queda congelado— hasta que exista la **corrección dirigida por tramo (id de apunte/tramo, decisión abierta de arriba)**. Hasta entonces el aviso de la UI es honesto: no promete que re-apuntar siempre funcione y **NO** sugiere marcar una AUSENCIA falsa (falsear el diario probatorio es inaceptable). Lo que importa queda a salvo: el diario en bruto —la prueba— permanece intacto aunque el total automático se pause.

### D37 — Capa derivada normalizada: se normaliza también el almacenamiento (Iulian + Claude, 2026-07-08)
Revisa la concreción de D24 ("normalizar la búsqueda, no el almacenamiento"). Detonante: las rarezas reales del corpus (periodos abril→marzo de Baleares, 4 nombres de campo para las pagas con 2 semánticas distintas) obligaban al motor a acumular lógica por convenio — la fuente del CRITICAL cazado en la PR #113.

- **Las transcripciones (`convenios/*.json`) quedan intactas** como fuente de verdad: espejo del boletín, verificadas celda a celda, auditables contra el PDF, y con todo lo que no son números (reglas de nocturnidad, disciplinario, permisos...) para el visor (D4), los tags (D35) y D29/D33.
- **Nueva capa derivada `convenios/normalizado/<id>.json`**: los mismos números como *hechos* planos uniformes — `concepto + dimensiones + desde/hasta (rango de fechas) + importe + articulo + rutaCruda`. Es lo ÚNICO que lee el motor: una sola lógica de búsqueda para los 55. Las vigencias como rango absorben las rarezas (abril→marzo = un rango más).
- **`rutaCruda` (JSON Pointer) = procedencia obligatoria** de cada importe, apuntando a la celda exacta de la transcripción.
- **Validador cruzado como test del build** (`CapaNormalizadaValidadorTest`): cada importe debe ser igual a la celda que cita su rutaCruda; sin solapes de vigencia; hechos completos. Si la capa diverge de la transcripción, el build rompe → la duplicación no puede divergir en silencio.
- **Regla de mantenimiento:** un importe NUNCA se corrige en la capa derivada; se corrige la transcripción (contra el PDF) y se re-deriva.
- Encaja con D24-Postgres: el hecho normalizado es literalmente la fila del seed (columnas fijas + dimensiones JSONB) y el bundle offline.
- Analogía que usamos: transcripción = código fuente; capa normalizada = compilado; validador = compilador.
- Estado: pilotos madrid-hosteleria (78 hechos, Anexo I) y baleares-hosteleria (54) en PR #114. Los 53 restantes: derivación mecánica por agentes, incremental.

### D36 — Los donantes pueden pedir funciones + extender a otras profesiones (Iulian 2026-07-08)
- **Donantes solicitan funciones:** en la sección de donaciones (D16), quien apoya el proyecto puede **proponer/solicitar features**. No es "pagar por desbloquear" (eso rompe D1); es dar voz en el roadmap a quien sostiene el proyecto. Transparente, tipo lista de deseos votada.
- **Extender a OTRAS PROFESIONES:** toda la arquitectura (convenios como JSON con su ESQUEMA, calculadora "te deben X€", condiciones, feed de curiosidades, denuncias) es **agnóstica del sector** — hostelería es solo la primera vertical. Los mismos problemas (horas extra no pagadas, convenios que nadie entiende, bajas mal cobradas) existen en comercio, limpieza, construcción, transporte, sanidad privada, teleoperadoras... El modelo se replica: mismo esquema, otro conjunto de convenios. Visión: de "app del trabajador de hostelería" a "app del trabajador" a secas.
- Consecuencia sobre el nombre (ya en D28): si se abre a todas las profesiones, "TeDeben" sigue valiendo (el mensaje "te deben X€" es universal), pero reforzar que no es solo hostelería.
- Estado: visión. v1 sigue siendo hostelería. Pero diseñar el modelo de datos ya pensando en `sector` como dimensión (no hardcodear "hostelería").

### D35 — Tags clicables de explicación en cada concepto (Iulian 2026-07-07)
- Al registrar su situación, el usuario ve una opción de **enlace/representación sindical** con un **tag clicable "¿qué es esto?"** que le explica en cristiano qué es, cómo se llega y qué le protege (art. 68 ET, crédito horario, fuero...).
- Generalizar el patrón: **cada concepto de la app lleva su tag clicable de explicación** en lenguaje llano — nocturnidad, complemento de baja, multa de haber (ilegal), polivalencia, prescripción de faltas, fijo-discontinuo... La app es educativa: no suelta un tecnicismo sin ofrecer explicarlo. Encaja con D18 (transparencia) y D27 (contenido que engancha).

### D34 — Cada dato con su fuente al nivel de artículo (Iulian 2026-07-07)
- Cuando la app le dé un dato al trabajador, debe decir DE DÓNDE sale: "según el **art. X** del Convenio de [zona/subsector]". No basta con citar el convenio: hay que citar el artículo/anexo concreto.
- Por qué: es una app de derechos; cada afirmación tiene que ser verificable por el propio trabajador (y por un abogado). Refuerza la confianza y el manifiesto (D18: "no me creas, compruébalo").
- Modelo de datos: cada dato relevante del JSON lleva su referencia (`articulo`, `anexo`, y la `fuente` con boletín/fecha ya existente). Ej.: nocturnidad → `"articulo": "Art. 27"`; tabla salarial → anexo y fecha del boletín. La app muestra esa cita junto al dato.
- Aplicar retroactivamente: revisar los JSON ya hechos para que cada dato tenga su artículo. Los agentes deben anotar el artículo de cada dato que transcriben.

### D33 — Capturar TODAS las condiciones importantes, no solo salarios (Iulian 2026-07-07)
- Hueco detectado: los JSON capturaban tablas salariales + jornada + nocturnidad + pagas, pero NO sistemáticamente las condiciones no salariales, que son igual de importantes para informar bien al trabajador. Ampliar el modelo de cada convenio para capturar:
  - **`vacacionesDias`** — días de vacaciones (varían: 30, 31...).
  - **`permisosRetribuidos`** — boda/matrimonio, mudanza, fallecimiento/enfermedad de familiar, nacimiento, exámenes, deber inexcusable... (días por cada causa; algunos convenios mejoran el ET).
  - **`complementoIT`** (baja médica) — CLAVE: muchos convenios complementan la prestación de la Seguridad Social hasta el 100% (o X%) del salario durante los primeros días/meses de baja por enfermedad/accidente. Dinero que casi nadie sabe que le corresponde. Distinguir baja por enfermedad común vs accidente laboral (suelen tener trato distinto).
  - **`antiguedad`** — trienios/quinquenios o antigüedad consolidada.
  - **`descansoEntreJornadasHoras`**, jornada partida (de D30).
- La app: sección "tus condiciones" por convenio + avisos ("estás de baja: tu convenio dice que la empresa te complementa hasta el 100% los primeros X días"). Encaja con D29 (derechos que no sabías).
- Los agentes que transcriben provincias deben capturar estos campos también (o reportarlos si no llegan); Claude los backfilllea al verificar.

### D27 — Feed de contenido laboral: PILAR de enganche/retención (Iulian 2026-07-07)
- **No es una sección secundaria: es una de las patas de la app.** Iulian lo quiere explícito: "que entres y te enganche mirando cosas curiosas sobre este tema laboral", no solo una herramienta de fichaje.
- **Por qué es estratégico:** una app de fichar se abre a regañadientes; un feed de contenido laboral que engancha se abre por gusto. Da una razón para abrir la app los días que NO fichas, y retiene al usuario hasta el día que necesita el "te deben X€". Resuelve el problema de retención de una utilidad pura.
- **Contenido:** curiosidades y rarezas de convenios ("¿sabías que...?"), derechos poco conocidos, noticias laborales, comparativas absurdas entre convenios. Todo con su fuente oficial.
- **Primer hallazgo real:** en el convenio de Hostelería de Madrid (Art. 27), la nocturnidad de **22:00 a 00:00 es solo del 1%** sobre salario base — con la fórmula `base×1%/(4×40)` sale literalmente **un par de céntimos por hora**. Trabajar hasta medianoche te renta casi nada; a partir de las 00:00 salta al 25%. Absurdo y real. (En hospedaje/hoteles el tramo 22-24h es del 20%: dos convenios de la misma ciudad, trato distinto.)
- Guardar rarezas conforme se transcriben convenios → el feed se llena solo con el trabajo que ya hacemos.

### D28 — Visión ampliada: de app a plataforma del trabajador (ideas de Iulian 2026-07-07, SIN comprometer)
- Ideas lanzadas: sección de **convenios y sus cosas**, **noticias laborales**, **portal de ofertas de trabajo decentes de verdad**, y **directorio de profesionales reales** ofreciendo sus servicios.
- Estado: VISIÓN a largo plazo (v3+), NO v1. Riesgo de scope creep: la v1 debe seguir siendo el núcleo (fichar + te deben X€) o no se lanza nunca. Un portal de empleo es un producto entero aparte (moderación, verificación de ofertas, masa crítica de dos lados).
- **Cómo hacer el portal de empleo (idea de Iulian 2026-07-07):** en vez de que las empresas publiquen, **scrapear ofertas de otros portales y mostrar SOLO las que cumplen el convenio** (salario ≥ tabla del convenio para esa categoría/zona). Tablón filtrado de "ofertas decentes de verdad". Diferenciador brutal: nadie hace un job board pre-filtrado por legalidad. Cautelas honestas: (1) muchos portales prohíben el scraping en sus términos — revisar legalidad/ToS o usar APIs/feeds oficiales; (2) verificar que una oferta "cumple el convenio" es difícil porque las ofertas raramente indican salario y categoría exactos → filtrar solo las que declaran salario, o estimar y marcar como "parece cumplir". v3+.
- **Consecuencia sobre el nombre:** si la visión de "plataforma del trabajador" se confirma, "TeDeben" (centrado en dinero adeudado) se queda corto. Reconsiderar el nombre ANTES de comprar dominio / hacer marca pública. De momento TeDeben sigue como nombre de trabajo (el dominio aún no está comprado; renombrar el repo es trivial). Pendiente de Iulian.

### D25 — El convenio es el MÍNIMO; salario real configurable + avisar de artimañas (Iulian 2026-07-07)
- El salario del convenio es un suelo, no lo que cobra cada uno. Al configurar su situación, el usuario indica si cobra: **el mínimo del convenio / más / menos** (menos = ya es ilegal, la app lo señala). Puede introducir su **salario real**, y el cálculo de "te deben X€" usa su hora real, no la del convenio (el convenio es la referencia/suelo).
- **Avisar de artimañas cuando se cobra "más" (idea de Iulian):** los empresarios suelen recuperar por otro lado lo que suben. La app informa de trucos habituales:
  - Bajar la **retención de IRPF** para inflar el neto → luego el trabajador paga en la declaración de junio.
  - Pagar parte como **"dietas"/"pluses" exentos** que no cotizan → menos paro, menos jubilación, menos base reguladora.
  - Salario "en negro" (ya cubierto en el comprobador de nómina).
- Objetivo pedagógico coherente con D18: que el trabajador entienda que "cobrar más" a veces es una trampa que le perjudica a medio plazo.

### D24 — Estrategia de datos de convenios: JSON en Git → seed → PostgreSQL (2026-07-07)
- **Dos capas.** (1) Ficheros `convenios/*.json` en el repo = fuente de verdad, versionada, con la fuente oficial de cada cifra; mantenimiento comunitario vía PR. (2) PostgreSQL = capa de consulta en runtime. Al desplegar, un proceso *seed* carga los JSON en la BD. La app consulta la BD, nunca los ficheros.
- **Por qué:** si los datos vivieran solo en la BD se perdería la trazabilidad (¿de qué boletín salió?) y la revisión pública. Con Git, cada número tiene fuente e historial. La BD se regenera desde los JSON.
- **Esquema flexible (clave, lo destapó Madrid-hostelería):** los convenios NO tienen estructura uniforme (hospedaje = nivel×grupo; hostelería = establecimiento×área×inicial/garantizado). El esquema no puede ser una columna por campo. Modelo: convenio → conceptos salariales tipados (clave-valor con dimensiones), no tablas rígidas. Evaluar JSONB en Postgres para la parte variable + columnas fijas para lo común (id, ámbito, vigencia, jornada anual, tope horas extra).
- Cobertura objetivo: ~150 convenios (≈50 provincias × 3 subsectores). Carga incremental por población; modo configurable a mano como respaldo mientras un convenio no esté transcrito.

**Concreción de la arquitectura (2026-07-08, tras ver la heterogeneidad real):**
- **Almacenamiento — Postgres HÍBRIDO:** columnas fijas para lo común y consultable (convenio_id, provincia, subsector, vigencia, jornada_anual, num_pagas, nocturnidad, tope_horas_extra, vacaciones_dias...) + columna **JSONB** para lo que varía entre convenios (tablas salariales con sus dimensiones propias, y el bloque de condiciones). Postgres indexa/consulta JSONB → velocidad SQL en lo común + flexibilidad en lo variable. Evita el error de "una columna por campo" (no cabe: 50 formas de tabla distintas).
- **Uso — MOTOR DE CÁLCULO GENÉRICO, no lógica por convenio:** cada convenio declara sus **dimensiones de salario** (p.ej. `["nivel","categoriaEstablecimiento"]`, o `["grupo","tazas"]`) y el motor resuelve la búsqueda recorriéndolas. Un solo cálculo para todos: `valorHora = (salario × pagas + plusesAnuales) / jornadaAnual`; encima nocturnidad/extra/festivos según los campos del convenio. **Normalizar la BÚSQUEDA (dimensiones declaradas), no el almacenamiento.**
- **Offline-first (encaja con D13):** los datos de convenio son de SOLO LECTURA y cambian ~1 vez al año → **empaquetarlos en la app** (bundle de los JSON) y actualizarlos por versión o sync ligera. El cálculo "te deben X€" no necesita servidor → funciona sin cobertura (sótano de cocina). El backend solo guarda los fichajes/cuadrantes del usuario.
- **Flujo de actualización:** JSON en Git (editable por PR) → script seed valida contra ESQUEMA.md → genera la BD y/o el bundle de la app. Regenerable en cualquier momento desde Git.
- **Validación en el seed:** el seed rechaza un convenio que no cumpla el ESQUEMA (campos obligatorios, cada dato con artículo) → red de seguridad contra huecos y datos sin fuente.

### D23 — Aviso por condiciones climáticas / calor extremo (idea de Iulian 2026-07-07)
- La app cruza la ubicación/jornada del usuario con una **API del tiempo**; si hay calor extremo (p. ej. 38°) o alerta AEMET, avisa al trabajador de sus derechos: puede no ser exigible ir/seguir trabajando.
- Base legal real (VERIFICADO EN LA RED 2026-07-07): **RD-ley 4/2023, de 11 de mayo** añade una disposición adicional al **RD 486/1997** sobre trabajo al aire libre. El disparador NO es una temperatura fija sino la **alerta naranja o roja de la AEMET**: cuando las medidas preventivas no bastan, es obligatorio adaptar/reducir/modificar la jornada. Aplica sobre todo a **trabajo al aire libre** (terrazas) y locales que no puedan cerrarse. El sueldo se mantiene íntegro si se reduce jornada (art. 23.3 RD 1561/1995).
- ⚠️ MATIZ: una cocina cerrada NO cae bajo esta norma de "aire libre" — va por las condiciones ambientales generales del RD 486/1997 (rangos de temperatura para trabajo ligero/sedentario). No prometer en la app "si hace X grados no vas a trabajar": lo correcto es "hay alerta AEMET naranja/roja → conoce tus derechos". Integración: AEMET OpenData (gratis, oficial) para leer alertas por zona.
- **Si va igual, lo anota y queda registrado (Iulian 2026-07-07):** aunque el trabajador acabe yendo (lo normal), puede marcar "trabajé con alerta de calor / 38° el día X" y la app lo guarda como prueba con fecha (la temperatura/alerta AEMET de ese día queda registrada automáticamente). Igual que los cuadrantes: evidencia acumulada. Sirve para reclamar condiciones o como agravante si hay un problema de salud.
- Diferenciador fuerte: ninguna app del sector avisa de esto.

### D21 — TDD siempre, sin excepción (Iulian 2026-07-07)
- Todo el código (backend y frontend) se hace con TDD: test primero, luego implementación. No negociable, en todo el proyecto.
- Backend: JUnit 5 + Mockito + MockMvc + Testcontainers (skills springboot-tdd / tdd-workflow). Frontend: Vitest + @vue/test-utils (configurado 2026-07-07).
- Tooling de calidad instalado en el scaffold: ESLint (eslint-plugin-vue) + Vitest en frontend; los reviewers java-reviewer/vue-reviewer se pasan tras cambios relevantes.

### D20 — Selección de categoría en cristiano + explicación del nivel (Iulian 2026-07-07)
- **Entrada fácil:** el usuario elige su **puesto real** de un desplegable (ayudante de cocina, cocinero, jefe de partida, jefe de cocina, camarero, pinche, fregador...), no un "nivel salarial" abstracto.
- **Pero SÍ se muestra el nivel y se explica** (matiz de Iulian): tras elegir, la app le dice "eres nivel 3 porque el convenio clasifica a cocineros/camareros ahí; tu salario base es X€" con enlace a su convenio completo. Objetivo pedagógico: que el trabajador ENTIENDA su convenio, que tiene siempre accesible en la app (encaja con el visor de convenio, D4). No ocultar el nivel: educarlo.
- Las categorías salen del ALEH VI (clasificación nacional común) → el desplegable es casi el mismo en toda España; solo cambia a qué nivel/salario mapea cada convenio.

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
- **Convenios (investigado 2026-07-07):** el ALEH VI (BOE-A-2023-6344) da la clasificación profesional común nacional; tablas en boletines provinciales (PDF); REGCON sin API; los textos oficiales no tienen copyright (art. 13 LPI) → transcribir es legal. Arquitectura: tablas como ficheros de datos en el repo (mantenimiento comunitario vía PR), cada tabla con su vigencia visible, + modo configurable de respaldo para provincias sin transcribir.
- **⚠️ SUBSECTORES (clave, Iulian 2026-07-07): "hostelería" NO es un solo convenio por provincia, son TRES ejes que se cruzan.** El usuario elige DOS cosas: (a) dónde trabaja [provincia/CCAA] y (b) tipo de establecimiento:
  1. **Hospedaje / hoteles** → convenio provincial o autonómico
  2. **Hostelería** (restaurantes, bares, cafeterías) → convenio provincial o autonómico DISTINTO del de hospedaje (verificado: en Madrid son dos convenios separados)
  3. **Restauración colectiva** (comedores de colegios, hospitales, empresas, residencias) → convenio ESTATAL único (BOE-A-2025-12598), igual en toda España
  Modelo de datos: añadir campo `subsector` al ámbito de cada convenio; el par (provincia, subsector) determina el convenio aplicable. El borrador de Madrid ya descubierto es de HOSPEDAJE (`docs/borrador-convenio-madrid-hospedaje.json`), NO cubre bares/restaurantes de Madrid.
- **Análisis de competencia (Iulian 2026-07-07):** al estudiar apps existentes (Sesame, Factorial...) la intención es SACAR IDEAS de UX/features, NO copiar código ni APIs. La skill `android-reverse-engineering` se usa solo para aprender de decisiones de diseño; extraer/replicar su propiedad intelectual queda fuera (choca con D18 y el plano legal).
- **Orden de carga de convenios (decisión de Iulian 2026-07-07): por población, de más a menos.** Primera tanda: 1) Madrid (autonómico), 2) Barcelona → convenio de Cataluña interprovincial (incluye Girona, Tarragona y Lleida gratis), 3) Valencia, 4) Alicante, 5) Sevilla. = 8 provincias y ~20M de habitantes cubiertos de salida. Iulian verifica las tablas transcritas (con ayuda de Claude) mientras un subagente codea.
- Git/fontanería lo hace Claude sin preguntar (ver CLAUDE.md global). Todavía **no hay repo** — crearlo cuando se decida arrancar.
- Iulian: castellano de España, tuteo, directo, mensajes cortos, él marca el ritmo. No cerrar cada mensaje con pregunta.
