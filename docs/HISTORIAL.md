# Historial del proyecto

Diario de lo que se va haciendo, una entrada por sesión o hito. Lo nuevo arriba.
Complementa al [ADR](ADR.md) (el ADR guarda *decisiones*; esto guarda *avance*).

## 2026-07-11 — QA general de la app y los cinco hallazgos arreglados (PRs #223-#227)

Primera pasada de QA funcional sobre la app entera (flujo anónimo, cuenta,
horario, libreta, resumen, informes PDF, borrado RGPD). El dinero cuadró de
punta a punta; salieron 5 issues (#218-#222) y los cinco quedaron arreglados,
revisados y mergeados el mismo día:

- **#221 → PR#225, el importante (dinero y evidencia)**: reconstruir un día
  apuntando la salida antes que la entrada dejaba el día "En curso" y esas
  horas desaparecían del resumen y del informe. Ahora la salida huérfana se
  empareja con la entrada posterior, pero SOLO cuando es inequívoco (mismo
  día, entrada anterior a la salida, una única huérfana; la ausencia la
  descarta como frontera). La revisión adversarial tumbó el emparejado
  ingenuo: habría fabricado jornadas nocturnas fantasma de 14 h (cruce de
  medianoche) y tramos mezclados en turnos partidos. Ante la ambigüedad no se
  auto-completa: un dato erróneo es peor que uno ausente.
- **#220 → PR#227 (decisión de producto)**: la sesión ya sobrevive a recargar.
  Se persiste SOLO el refresh token (rotativo, revocable, con detección de
  robo — B4 intacto) en localStorage con acceso defensivo; el access y el
  email siguen únicamente en memoria. Restauración silenciosa al arrancar con
  guardia async; coordinación entre pestañas (compare-and-delete) para que una
  pestaña perdedora de la rotación no purgue el token de la ganadora. E2E
  nuevo en CI: "recargar mantiene la sesión". Matiz de D38 documentado en el
  store: una credencial revocable no es el diario.
- **#218 → PR#224**: el arranque documentado (`mvn spring-boot:run`) volvía a
  arrancar activando el perfil dev desde la configuración del plugin (la
  barrera del secreto JWT, H1, queda intacta). Ojo verificado en vivo: el
  perfil del plugin viaja como argumento de programa y le gana a
  `SPRING_PROFILES_ACTIVE`; para otro perfil, `-Dspring-boot.run.profiles`.
- **#219 → PR#223**: la clasificación decía "tu puesto es barcelona" (colaba
  la zona como puesto). Ahora nombra el puesto elegido y etiqueta cada
  dimensión; de propina cayó un bug real de etiquetas (sinónimo corto contra
  etiqueta larga, reproducido con los datos de Cádiz).
- **#222 → PR#226**: las citas de fuentes ya dicen "1.705,13 €/mes" en vez de
  "1705.13 EUR/mes", reutilizando el formateador del informe PDF.

Todo con TDD (el caso literal del QA es ahora un test de regresión), CI verde
incluidos los E2E, y verificación final de los cinco flujos sobre la app real.
Dos sospechas del QA resultaron falsas alarmas de la automatización del
navegador, no bugs — quedaron descartadas tras investigarlas.

## 2026-07-10 (noche) — sesiones revocables: refresh rotativo y logout real (B4)

El último punto activo de la arquitectura. Antes: JWT de 24 h imposible de
revocar (un token robado valía un día entero; tras borrar la cuenta seguía
siendo criptográficamente válido). Ahora:

- **Access JWT de 15 min**, stateless como siempre (validar no toca BD).
- **Refresh opaco de 256 bits** en la tabla `sesiones` (V6), guardado como
  SHA-256 — una fuga de BD no reconstruye tokens — y con `ON DELETE CASCADE`:
  el borrado de cuenta revoca gratis.
- **Rotación con detección de robo**: cada refresh se gasta UNA vez
  (reclamación atómica por UPDATE); si llega uno ya gastado, se revocan TODAS
  las sesiones del usuario. **Bug real cazado por la verificación en vivo**
  (no por los mocks): el 401 del reuso hacía rollback de la propia revocación
  → `noRollbackFor` + test de integración con transacciones reales.
- **/auth/refresh y /auth/logout** públicos (viajan con el refresh en el body;
  un access caducado en la cabecera daría 401 espurio). Logout ahora revoca
  en el servidor.
- **Cliente API**: un 401 intenta renovar UNA vez (single-flight: N peticiones
  concurrentes comparten un refresh) y reintenta con el token rotado; si no,
  la expulsión de siempre. Peticiones `anonimo` (refresh/logout) van sin
  Bearer y no disparan renovación (sin bucles). Guardia de sesión cruzada: un
  refresh que resuelve tarde no resucita una sesión cerrada (y el token zombi
  se revoca).
- Los tokens siguen SOLO en memoria (la norma no cambia); persistir sesión
  tras recargar es decisión aparte (v2 nativa, secure storage).

## 2026-07-10 (noche) — E2E de Playwright en CI: los recorridos críticos, vigilados

Hasta hoy los viajes completos solo se comprobaban a mano (QA en navegador o
emulador). Ahora corren en cada PR contra el stack REAL (vite dev + Spring
Boot + Postgres de servicio), en un job propio de CI.

- **4 recorridos**: el viaje crítico entero (registro → perfil con la pregunta
  encadenada del convenio → horario semana tipo → fichar entro/salgo → "Lo
  tuyo" carga SIN la guía del 422); la consulta anónima de salario mínimo con
  su cita del boletín (Zaragoza camarero, el mismo QA del rediseño); y el
  borrado de cuenta RGPD por las dos ramas (contraseña buena y mala).
- Solo Chromium a propósito: la app de producción ES un WebView Chromium
  (Capacitor). Esperas deterministas, cuentas de usar y tirar por test,
  navegación por la interfaz (el token vive en memoria: un goto() recarga la
  SPA y pierde la sesión — está documentado en el helper).
- `MEDEBEN_API` en vite.config permite apuntar el proxy a un backend paralelo
  (así se verificó en local sin tocar el stack de desarrollo).
- Cazas del propio E2E al escribirlo: el select de puesto de la calculadora se
  REMONTA cuando llega el convenio (había que esperar la tarjeta antes de
  elegir), y el onboarding de la libreta bloquea el primer fichaje si no se
  recorre.
- vue-tsc tipa también los specs (tsconfig.node) y vitest los excluye (cada
  runner lo suyo).

## 2026-07-10 (noche) — backups de Postgres, con ensayo de restauración

La BD de producción tenía CERO copias, y guarda la evidencia de los usuarios.

- `deploy/backup-db.ps1`: pg_dump (formato custom) del servicio `db` del
  compose de prod, verificado con `pg_restore --list` ANTES de guardarse (un
  dump corrupto no se guarda), copiado a `Documents\medeben-backups\` +
  copia externa opcional (`-CopiaExterna`, para OneDrive/disco externo),
  retención de 30 días. El dump nunca cruza un pipe de PowerShell (5.1
  corrompe binarios): fichero en el contenedor + `docker cp`.
- `deploy/restaura-db.ps1`: ensayo INOFENSIVO en un Postgres efímero (restaura,
  cuenta filas por tabla, destruye) y modo desastre sobre producción con
  confirmación escrita. Regla: un backup solo existe si se ha restaurado.
- Verificado de verdad contra el prod local: primer backup hecho y su ensayo
  restaura las 4 tablas (usuarios/perfiles/cuadrantes/apuntes).
- `docs/backups.md`: uso, tarea diaria programada de Windows (03:30) y el
  equivalente para cuando haya hosting Linux. Enlazado desde despliegue.md.
- Trampas de PowerShell 5.1 documentadas en los propios scripts: ficheros
  .ps1 CON BOM (sin él, un guion largo UTF-8 se lee como comilla tipográfica
  y rompe el quoting) y nada de `2>$null` sobre exes con ErrorAction Stop.
- Pendiente de Iulian: registrar la tarea programada (una vez, como admin) y
  elegir la carpeta de copia externa.

## 2026-07-10 (noche) — borrado de cuenta (RGPD art. 17): el bloqueante de Play Store

El derecho de supresión, de punta a punta. Era el hueco nº1 del análisis de
arquitectura (sin él no hay política de privacidad honesta que publicar).

- **Backend**: `DELETE /api/v1/cuenta` borra FÍSICAMENTE al usuario del token
  re-confirmando su contraseña; el `ON DELETE CASCADE` del esquema (que ya
  estaba preparado) arrastra perfil, cuadrantes y apuntes, y el email queda
  libre (test de cascade contra Postgres real). Contraseña incorrecta → 403
  (un 401 expulsaría la sesión); token vivo de cuenta ya borrada → 401.
- **Frontend**: zona de borrado al pie de Cuenta en dos pasos — aviso de que
  el borrado destruye la evidencia y hay que descargar antes los PDF
  mensuales, y solo después la advertencia final con contraseña. Al éxito se
  limpia la sesión entera (regla de dispositivo compartido) y se vuelve a la
  portada.
- **Reviews aplicadas** (java + security-Opus + vue): la purga de la caché de
  informes anuales pasa a DESPUÉS del commit (TOCTOU: una petición concurrente
  podía repoblarla con el PDF del borrado); el DELETE gasta el presupuesto
  ESTRICTO de rate limit (es una re-confirmación de contraseña, como el
  login), con clave ip|sub; y en Vue, un borrado que resuelve tarde ya no pisa
  la sesión de OTRO usuario que entró mientras tanto, más foco gestionado,
  `role="alert"` en la advertencia y token propio `--sobre-alerta`.
- Limitación conocida y aceptada (es el punto B4 del plan, refresh/revocación):
  el JWT stateless sigue siendo válido hasta 24 h tras el borrado; no puede
  leer nada (los datos ya no existen) ni crear huérfanos (FK), y un POST con
  él devuelve un 500 ruidoso en vez de un 401 fino.

## 2026-07-10 (noche) — la restauración colectiva calcula: 405 pares puesto×provincia

El convenio estatal de restauración colectiva no tenía ningún puesto mapeado
(cada anexo provincial agrupa ocupaciones en categorías literales propias: 296
literales en 48 provincias). Ahora un cocinero de comedor escolar elige su
puesto, responde su provincia y llega a su fila salarial.

- **Motor**: nueva forma `condicionalPorProvincia` — el árbol condicional
  declara su dimensión objetivo (`Condicional(arbol, dimensionObjetivo)`): los
  árboles de establecimiento/zona siguen resolviendo `nivel`; los de provincia
  resuelven la `categoria` literal del anexo. `NodoCondicional` intacto.
- **Datos**: piloto manual (Cáceres) → flota de 94 agentes (mapeador +
  refutador adversarial por provincia) → arbitraje manual de 42 discrepancias
  → validación byte a byte contra la capa normalizada + barrido de cobertura
  (0 callejones). Detalle y criterios: `convenios/colectiva-ocupaciones-informe-2026-07-10.md`.
- **Frontend**: solo etiquetas (la pregunta "provincia" ya era genérica);
  tildes para las provincias ASCII de la capa normalizada.
- No mapeable hoy (honesto): Alicante (anexo solo con niveles sin ocupaciones),
  Lugo (tabla por tenedores sin normalizar), y las figuras de hostelería
  clásica que colectividades no contempla.

## 2026-07-10 — rediseño de la UI de cero: "Nómina clara" (PR #192)

Iulian pidió rehacer la interfaz entera desde cero, con la skill impeccable
como guía de diseño y anime.js para el movimiento. La lógica (stores,
services, lib, router) no se tocó; las 17 piezas visuales (8 vistas, 8
componentes y el shell) se reescribieron sobre un sistema nuevo.

- **Sistema "Nómina clara"** (dirección elegida por Iulian entre tres
  propuestas): la claridad de un documento oficial, a favor del trabajador —
  papel blanco, tinta casi negra, y el verde reservado para el dinero, la
  acción y la selección. Tokens OKLCH claro/oscuro y una capa de componentes
  globales en `style.css` para que toda la app hable el mismo vocabulario.
  `PRODUCT.md` y `DESIGN.md` en la raíz capturan estrategia y contrato visual.
- **Public Sans variable auto-hosteada** (subset latín de 25 KB hecho a mano
  con fontTools conservando los numerales tabulares que Google Fonts elimina:
  sin ellos, la cifra bailaría al animarse). Precacheada por el service worker.
- **Motion con anime.js 4.5 vía `lib/animacion.ts`**: la cifra del resumen
  cuenta de 0 al importe y un trazo verde la subraya al llegar (el momento
  firma); listas recién cargadas entran escalonadas; despliegues inline con
  `PanelPlegable` (la alternativa de la casa al modal). `prefers-reduced-motion`
  recibe el estado final al instante, sin excepciones, y en jsdom las
  animaciones son síncronas (los tests no dependen de frames).
- **Construcción en paralelo:** la fundación y la pantalla héroe a mano; las
  otras siete vistas, seis agentes en paralelo con el contrato de diseño en el
  prompt. En la libreta, el botón primario verde cambia con el estado del día
  (Entro ↔ Salgo), con los dos siempre disponibles por las jornadas partidas.
- **Review multi-agente** (Vue, diseño, accesibilidad) con verificación
  adversarial por hallazgo: 8 confirmados y arreglados — entre ellos el
  contraste 3:1 de los bordes de controles (WCAG 1.4.11) en ambos temas y el
  `aria-invalid`/`aria-describedby` de los formularios — y 3 refutados con la
  razón documentada.
- **QA en navegador contra el stack real** (registro → perfil → fichar →
  semana → resumen; consulta anónima Zaragoza camarero → 1.351,82 € al mes con
  sus citas). De ahí salió un endurecimiento real: la transición de vistas
  ahora se cierra por timer (`:duration` explícita) porque un `transitionend`
  perdido con el webview en segundo plano podía dejar la pantalla en blanco.
- Suite frontend 286/286, cobertura 91,3 % / 88,9 % (gate 80 %), eslint limpio,
  build y precache OK. Pendiente para Iulian: QA manual en dispositivo real
  (sensación del motion en gama baja, render de la fuente) y regenerar el APK.

## 2026-07-09 — maratón nocturno · La app queda funcionalmente completa

Sesión autónoma larga (Iulian fuera, con orden de no parar): 17 PRs mergeadas
y los tres frentes cerrados. MeDeben ya hace de punta a punta lo que promete.

- **Limpieza de la cola:** las 8 PRs de datos de convenios pendientes (Alicante,
  Jaén, Pontevedra, Asturias, Las Palmas, Málaga, Valencia, Melilla) y 4 de
  dependabot mergeadas; quedan retenidas con nota las 2 majors en rojo (Spring
  Boot 4, TypeScript 7) y 3 que piden el scope `workflow` del token de gh
  (mergeables desde la web).
- **PR #163 — "me deben X € este mes" (backend), con review adversarial previa
  (Opus):** la review confirmó un N+1 de horario (~52-106 SELECTs por petición
  recorriendo el año) → nuevo `horariosEfectivosDelRango` (2 consultas y
  resolución as-of en memoria, misma semántica D38) con test de regresión;
  cota inferior de mes (2019) y el input crudo fuera de los errores RFC 7807.
- **PR #152 — libreta en el frontend, con review adversarial previa:** el
  hallazgo gordo era un mutex de fichaje que podía quedarse pegado para
  siempre (navegar durante un POST lento deshabilitaba los botones de fichar
  hasta cerrar sesión) → liberación incondicional + test; la semana ya no se
  cae entera por un día con error (allSettled por día); la rectificación
  tardía re-comprueba la confirmación en el punto de envío; y el contador de
  cierre de D38 ahora cuenta de verdad ("se sella el jueves 23/07 — en 14 días").
- **PR #166 — coverage frontend:** `pwa.ts` salía EXCLUIDO del coverage en
  silencio (parse error del TS crudo en ficheros sin test) → stub del módulo
  virtual + tests reales; PerfilView pasó de 0 tests a cubierta; gates de
  vitest en las 4 métricas al 80 %.
- **PR #167 — pantalla "Lo tuyo, este mes":** el número gordo de la app, con
  navegación de meses, 422 tratado como guía (no error) con enlace a lo que
  falte, tope anual D22 con barra, avisos y citas D18. Vue-review con 7
  hallazgos (0 críticos), todos aplicados.
- **PR #168 — rate limiting transversal (blocker pre-deploy de la auditoría):**
  token buckets propios en memoria, presupuesto estricto por IP en auth y por
  usuario en el resto; la review de seguridad (Opus) tumbó la primera versión
  (DoS de memoria vía subs forjados y 429 a plantillas tras un mismo WiFi) y
  la final lleva tope duro de cubetas con desbordamiento por grupo, límites
  de auth dimensionados para CGNAT y health/OPTIONS exentos.
- **PR #169 — recordatorio diario de fichar:** el módulo de notificaciones de
  #151 por fin tiene UX (solo app nativa): toggle + hora, permisos con
  fallback honesto, quincena renovada sola, solo se persiste la hora.
- **PR #170 — pasada de diseño pedida por Iulian:** acento verde-dinero
  semántico (antes el accent ERA el color del texto), barra de navegación
  inferior (Lo tuyo / Libreta / Cuenta), nombre visible MeDeben, focus-visible
  global y prefers-reduced-motion.
- **PR #171 — deep rename a MeDeben:** paquete `es.medeben`, appId
  `es.medeben.app` (antes de Play Store: es inmutable), properties, claves de
  localStorage, docker-compose y dev-setup. ADR e HISTORIAL conservan las
  menciones históricas.
- **PR #172 — release Android + deploy:** icono y splash de MeDeben (marca de
  euro geométrica, 74 assets), firma de release con keystore fuera del repo,
  `assembleRelease` firmado verificado; Dockerfile de backend, compose de
  producción y nginx con las cabeceras de seguridad de la auditoría
  (pendiente de verificar el build de imagen: sin Docker en la máquina).
- **Pendientes que quedan** (decisiones de Iulian): dominio + HTTPS + hosting,
  cuenta de Play Store y política de privacidad, bloqueo por cuenta
  anti fuerza bruta, PK UUIDv4, id de tramo/apunte en el API, docs personales
  del repo público, y QA manual del diseño nuevo con la app en marcha.

## 2026-07-09 — mañana · Nombre y honestidad

- **PR #164:** nota de autoría honesta y estado WIP en los README (EN/ES).
- **PR #165:** rename de superficie TeDeben → MeDeben en los README; el repo
  pasa a `github.com/iulian640/medeben`. El deep rename se difirió (y cayó
  esa misma noche, ver arriba).

## 2026-07-08 — noche · Auditoría integral + ola de fixes en 5 frentes

- **Auditoría integral pedida por Iulian** (43 agentes: 8 auditores en paralelo sobre TODO
  el repo, verificación adversarial de cada hallazgo y crítico de completitud): 16/16
  arreglos de reviews pasadas siguen en su sitio (cero regresiones), CI verde reproducida
  en local, 2 CRITICAL y 3 HIGH nuevos confirmados. El informe detallado queda fuera del
  repo (es público y listaba vulnerabilidades entonces sin arreglar).
- **PR #140 — seguridad y perfil:** la barrera del secreto JWT de juguete ignoraba el caso
  exacto que decía proteger (sin `SPRING_PROFILES_ACTIVE`, el fallback a los perfiles por
  defecto la saltaba) → ahora solo mira perfiles activos; el PUT de perfil ya es upsert
  real con reintento anti-carrera (antes la segunda edición daba 500 por PK duplicada);
  401 sin token en RFC 7807 (`ProblemDetailEntryPoint`); `toString()` de los DTO de
  fichaje redacta el motivo (art. 9 RGPD); migración V5 con CHECKs de vocabulario; tope
  de valores de dimensiones subido a 400 (el de 100 rechazaba ~250 valores legítimos del
  catálogo, el más largo con 325 caracteres).
- **PR #136 — turno partido:** `estadoDia()` se quedaba solo con la última entrada/salida →
  un partido fichado (12-16 y 20-23) contaba 3 h en vez de 7, en silencio. Reescrito con
  emparejado secuencial de tramos (ampliación de D38); los reviewers bloquearon la primera
  versión y de ahí salieron el techo de cordura de 16 h/tramo y la corrección simétrica
  de entradas.
- **PR #138 — frontend:** cerrar sesión limpia también el store de cuenta e invalida las
  peticiones en vuelo (en un dispositivo compartido el siguiente usuario veía los datos
  salariales del anterior); recarga controlada del service worker en cada deploy y
  `router.onError` con anti-bucle para los chunks huérfanos tras un deploy.
- **PR #139 — corpus legible:** el valor hora ya resuelve para 49 de 55 convenios (antes
  44); jornadas de Cataluña/Gipuzkoa/Soria y pagas de Lugo re-expresadas bajo claves
  canónicas sin tocar un solo valor; Tenerife estrena `divisorValorHora` con soporte en
  el motor y copy propio en la calculadora. Los 6 restantes tienen el dato genuinamente
  ausente en la fuente (documentado en `convenios/revisiones-pendientes.md`).
- **PR #137 — cobertura y CI:** JaCoCo (backend 91,4 % de líneas) y coverage de Vitest
  (frontend 80,5 %) con gate al 80 %; artifacts de test en la CI, acciones pineadas por
  SHA, dependabot, y Mockito registrado como agente (adiós al warning de JDK 21).
- Pendientes que deja la auditoría: rate limiting transversal (blocker pre-deploy, ya
  conocido), cabeceras de seguridad al desplegar el frontend, PK UUIDv4 en tablas
  append-only (decisión de diseño pendiente), id de tramo para correcciones dirigidas
  (decisión de producto), y qué hacer con los docs personales del repo público
  (decisión de Iulian).

## 2026-07-08 — noche · Horario: arranca la libreta sellada (D38)

- **Decisiones de producto cerradas con Iulian (ADR D38):** día = libre / seguido /
  partido (máx. 2 tramos); semana tipo que se repite + ediciones por semana con
  historial fechado; nada se auto-asume (sin confirmación no hay dato); ventana de
  14 días para confirmar y el día se **sella**; después del sello solo rectificación
  tardía en registro propio (idea de Iulian: como la contabilidad, no se borra, se
  rectifica con apunte nuevo); los huecos se quedan huecos. Razón probatoria: el sello
  de hora responde "¿cuándo lo apuntó?" y el sellado "¿pudo manipularlo después?".
- **Backend del horario (TDD, 24 tests nuevos, 136 en total):** entidad `Cuadrante`
  append-only (el repositorio ni siquiera expone borrar), resolución del horario
  efectivo por semana usando la versión vigente ENTONCES (el pasado no se reescribe),
  turno de cierre que cruza medianoche solo en el último tramo, guarda de semana
  sellada → 409, reloj inyectable (`RelojConfig`, zona peninsular; Canarias pendiente).
  Endpoints: GET/PUT `/api/v1/horario` y `/api/v1/horario/semana/{lunes}`.
- **README bilingüe (PR #132, agente):** `README.md` en inglés por defecto +
  `README.es.md`, `docs/dev-setup` igual; términos legales españoles con glosa;
  pasado por la skill avoid-ai-writing. Docs internos siguen en castellano.
- **Diario de fichajes (TDD, 22 tests nuevos):** apuntes append-only (entrada /
  salida / ausencia con motivo) con **origen probatorio** — CONFIRMADO si se ficha
  antes del mediodía siguiente (cubre el turno de cierre fichado de madrugada),
  RECONSTRUIDO dentro de la ventana de 14 días, RECTIFICACION_TARDIA tras el sello
  (solo con confirmación explícita; sin ella → 409). Estado del día derivado del
  diario (pendiente / en curso / completo / ausencia / hueco), minutos trabajados
  con cruce de medianoche, y `selladoDesde` para el contador de la UI. Endpoints:
  POST /api/v1/fichajes y GET /api/v1/fichajes/dia/{fecha}.
- **Docker en la máquina de Iulian**: Docker Desktop instalado; Testcontainers no
  arrancaba (Docker 29 rechaza API < 1.40 y el docker-java de Testcontainers 1.21
  cae a 1.32) → `docker-java.properties` con `api.version=1.44` + tubería correcta
  en `~/.testcontainers.properties`. Los tests de integración ya corren en local.
- **PR #134 mergeada** tras Approve de java-reviewer (re-verificación) y CI verde.
  El CI cazó de verdad: el test del desempate comparaba UUIDs al estilo Java y
  Postgres los ordena como bytes sin signo — arreglado comparando el hex.
- Frontend auth **mergeado (PR #133, agente)**: login/registro, token solo en
  memoria, guard de router, cuenta con perfil del servidor; ambos reviewers APPROVE;
  frontend de 46 a 93 tests.
- Siguiente: agregado "te deben este mes" (horario efectivo vs diario → motor de
  cálculo) y Capacitor (Android Studio ya instalado en la máquina de Iulian).

## 2026-07-08 — tarde/noche · Arranca la fase APP

- **Frontend de autenticación + cuenta (Vue)**: pantallas de login y registro
  (validación en cliente calcada a los límites del backend, errores RFC 7807 legibles),
  store Pinia de sesión con el JWT **solo en memoria** (nunca localStorage — al recargar
  se pide login de nuevo, aceptado para v1, guía del security-reviewer), cliente API con
  Bearer automático y manejo de 401 sin bucles (login fallido ≠ sesión caducada), guard
  de router para `/cuenta` con `?redirect=` blindado contra open redirect, y vista de
  cuenta que carga/guarda el perfil del servidor (el PUT es full-replace: siempre se
  manda el objeto completo). TDD estricto (todo test en rojo primero); revisada por
  vue-reviewer y security-reviewer (ambos APPROVE, cero CRITICAL/HIGH; el MEDIUM de
  cada uno corregido: token de solo lectura fuera del store y flag anti-doble-push en
  el manejador de 401). El flujo anónimo sigue intacto.
- **Perfil laboral persistido** (provincia, puesto, dimensiones, salario real): el
  servidor resuelve el convenio (nunca se confía en el del cliente) y el id de usuario
  sale siempre del token (sin acceso cruzado, verificado por security-reviewer: cero
  CRITICAL/HIGH). java-reviewer cazó dos finos de JPA (Persistable para evitar el
  merge+SELECT, @Version contra pérdidas de actualización, actualizado_en que nunca
  se actualizaba) — corregidos.
- **El mínimo se explica solo**: desplegable "¿De dónde sale este mínimo?" con la
  cuenta del usuario (base × pagas + pluses ÷ jornada, suelo del art. 35 ET).
- **Escaparate suavizado** (decisión de Iulian, busca trabajo): README educativo con
  la skill de escritura, secciones de ingeniería e hitos; el rename sigue en reposo
  (finalistas verificados: Jornalia, MiConvenio, EnClaro, TusHoras).
- **Grafo de código limpio**: solo fuentes reales, 183 nodos etiquetados (fuera el
  ruido de configs que lo hacía espagueti).
- **Registro/login con JWT (D13.4)**: primera migración Flyway (tabla usuarios, solo
  email+hash — minimización RGPD), BCrypt, HS256 con secreto por entorno, endpoints
  /auth/registro, /auth/login y /me. Revisado por java-reviewer (BLOCK: bin/ colado en
  git + carrera TOCTOU del registro → arreglados) y security-reviewer (sin CRITICAL;
  mitigaciones anti-enumeración/anti-timing VERIFICADAS; H1 arreglado: barrera fail-fast
  que impide arrancar fuera de dev/test con los secretos de juguete del repo; tope 72
  bytes de BCrypt). Trackeado pre-despliegue: rate limiting en auth, y el token del
  frontend SOLO en memoria (nunca localStorage) — guía del security-reviewer.
- **Pantalla de perfil v0 (Vue)**: flujo provincia → tipo de sitio → puesto →
  "tu salario mínimo" con citas enlazadas + calculadora de horas extra. Construida por
  agente, revisada por vue-reviewer (BLOCK inicial por 2 bugs de corrección — carreras
  async y prellenado congelado — arreglados con TDD y re-verificados: APPROVE). 42 tests.
- **PR #124** — Re-verificación contra PDF oficial de las 4 contradicciones del mapeo:
  Sevilla y Cádiz eran errores nuestros (corregidos: camarero Sevilla = Nivel 3);
  Valladolid es errata del propio BOP (duda UGT); la celda del BOE de Córdoba estaba
  bien tratada (errata del BOE sin corrección publicada).
- **Mapeo puesto→nivel completo (D20)**: lista curada de 16 puestos + backend de
  resolución (puesto → dimensiones fijas + preguntas pendientes con valores reales) +
  endpoints GET /puestos y /convenios/{id}/puestos/{p}; 10 agentes mapearon los 54
  convenios (~490 puestos fijados; el resto null documentado — nunca se adivina).
  Validador cruzado en el build. Triaje de rarezas: 8 curiosidades nuevas al feed,
  contradicciones (Sevilla camarero N3-vs-N4, Cádiz, Valladolid) a re-verificar contra
  PDF + preguntas UGT, y huecos estructurales (mapeo condicional, colectiva por
  provincia, transcripciones a completar) a revisiones-pendientes.
- **PR #122** — Citas con enlace (pedido por Iulian): cada cita pasa a `{texto, url}` —
  las del convenio enlazan al PDF oficial del boletín y las del ET al BOE consolidado
  ("no me creas: compruébalo", D18/D34). **PR #121** — perfil `local` sin BD para probar
  la API sin Docker.
- **PR #119** — Primera API REST pública: consulta de convenios (lista, detalle para el
  visor D4, selección provincia+subsector D20) y cálculos anónimos (horas extra y salario
  base) con citas. RFC 7807 en todos los errores, 500 saneado y testeado, Cache-Control en
  datos estáticos. Revisada por java-reviewer (HIGH del contrato de errores corregido) y
  security-reviewer (sin CRITICAL; @Digits anti-DoS aplicado; rate limiting trackeado para
  antes del despliegue público).
- **PR #117** — CI en GitHub Actions: backend con Docker (Testcontainers + validador del
  corpus en cada PR) y frontend (lint + vitest + build). Verde a la primera.
- **PR #118** — El lookup expone la `unidad` del hecho (Cuenca publica en EUR/año).
- **Capa normalizada completa**: 10 agentes en paralelo derivan los 52 convenios restantes
  → **54/55 con capa derivada, ~8.760 hechos `salarioBase`**, todos con procedencia
  (`rutaCruda`) verificada por el validador del build. `aleh-estatal` sin fichero (correcto:
  es acuerdo marco sin tablas). Huecos honestos documentados (Lugo en colectiva, errata BOE
  en una celda de Córdoba, regímenes especiales fuera de alcance v1).
- **PR #115** — ADR D37: decisión de normalizar también el almacenamiento como capa derivada
  (transcripciones intactas + hechos planos + validador cruzado).
- **PR #114** — Capa derivada normalizada (pilotos Madrid 78 hechos y Baleares 54),
  `CapaNormalizadaValidadorTest` (el validador cruzado, corre en cada build) y
  `TablaSalarialService` (lookup por dimensiones + fecha, ultraactividad avisada en citas).
  Test de cadena completa: tabla → motor → cocinero de Madrid a 10,8979 €/h.
- **PR #113** — Motor de cálculo v1 (`CalculoConvenioService`): valor hora ordinaria,
  horas extra (suelo art. 35.1 ET, precio del convenio si es mayor), tope anual (convenio u
  80 h ET), todo con citas de artículo (D34). El java-reviewer **bloqueó** la primera versión
  por un fallo real (pagas de cuantía fija inflaban el valor hora) → campo canónico
  `mensualidadesEquivalentes` backfilleado en 7 convenios; re-verificado y aprobado.
- **PR #112** — Capa de datos: modelo de dominio (`Convenio`, `Vigencia`...), `ConvenioCatalog`
  (carga y valida los 55 JSON al arrancar, fail-fast) y selección D20
  `paraTrabajador(provincia, subsector)` con test de cobertura 52 provincias × 3 subsectores.
- Datos normalizados: `ambitoFuncional`/`ambitoTerritorial` de Jaén, id de madrid-hospedaje,
  pagas de Ciudad Real (`total: 3` → `cantidad: 3`).
- Tooling: grafo de conocimiento del repo con graphify (`graphify-out/`, local); limpieza de
  todas las ramas y worktrees viejos de agentes.

## 2026-07-08 — madrugada/mañana · Corpus de convenios COMPLETO

- Loop autónomo de agentes: **55 convenios transcritos, cobertura territorial 100 % de España**
  (50 provincias + Ceuta + Melilla; Madrid/Gipuzkoa/La Rioja con hospedaje aparte;
  ALEH VI; colectiva estatal con sus 49 anexos provinciales completos).
- Spot-checks independientes contra el PDF oficial: 0 discrepancias en miles de celdas.
- PRs #103-111: los 13 convenios a medias completados (Málaga articulado entero, Cataluña
  Anexo II, Madrid Anexo I por niveles...).
- Requisito del motor detectado por Iulian mirando Teruel: convenio vencido ≠ caducado
  (**ultraactividad** — aplica la última tabla publicada).
- Dudas UGT investigadas con fuentes oficiales → `docs/dudas-resueltas/` (clave: las
  vacaciones se pagan a retribución media, no solo salario base).
- Ficheros de control: `convenios/ESQUEMA.md` (checklist obligatoria), `INDICE.md` (roadmap),
  `ESTADO.md` (vigencia/currency por convenio).

## 2026-07-07 — Definición completa + arranque del repo

- Sesión larga de producto: ADR con las decisiones D1-D35 (gratis para el trabajador,
  stack Java/Spring + Vue 3, la app te persigue para fichar, subsectores, TDD siempre,
  cada dato con su artículo, feed de contenido como pilar...).
- Nombre elegido: **TeDeben** (dominios y GitHub verificados libres).
- Repo público creado (AGPL): scaffold Spring Boot 3.5 + Vue 3/Vite/TS/PWA (PRs #1-2),
  docker-compose Postgres, Flyway, Testcontainers.
- Primeros convenios transcritos con el método definitivo: **renderizar el PDF oficial a
  imagen y leer celda a celda** (nunca el texto extraído, desalinea columnas). Madrid
  hospedaje verificado 100 %; regla de oro: un dato erróneo es peor que un dato ausente.

## 2026-07-04 — La idea

- Anotada la idea: app del lado del TRABAJADOR de hostelería — registra tu horario,
  guarda tus cuadrantes como prueba, calcula lo que te deben según tu convenio y te ayuda
  a reclamarlo. El hueco: todo lo que existe es B2B del lado de la empresa.
