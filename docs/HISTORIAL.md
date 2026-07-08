# Historial del proyecto

Diario de lo que se va haciendo, una entrada por sesión o hito. Lo nuevo arriba.
Complementa al [ADR](ADR.md) (el ADR guarda *decisiones*; esto guarda *avance*).

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
