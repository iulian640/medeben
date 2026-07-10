# Inventario de huecos de DATOS (2026-07-10)

> Qué falta en el corpus que **afecta al cálculo** (salario, valor hora, horas
> extra). Los cientos de marcadores de `regimenDisciplinario`/`vacaciones`/
> `igualdadYAcoso`/`representacionSindical` NO entran aquí: son condiciones no
> salariales. Regla de oro: **nunca inventar**; dato ausente > dato erróneo.

## A) Tablas de AÑOS FUTUROS (no existen aún — no se pueden buscar)

Muchos convenios marcan `2027`/`2028` como `pendiente` porque **no se han
publicado** (se fijarán tras el IPC). Es correcto que estén ausentes; el motor
aplica la última tabla por ultraactividad. NO es un hueco que se pueda cerrar hoy.
Ejemplos: Álava, Gipuzkoa, Castellón (2027/2028), Cantabria 2026, Ciudad Real
2026, etc.

## B) Tablas PUBLICADAS pero NO transcritas (SÍ se pueden buscar → transcribir)

Existe la fuente oficial; falta pasarla al JSON con el método del proyecto
(renderizar PDF → leer imagen celda a celda → verificar):

- **Castellón 2025 definitivas** (BOP nº112, 18-09-2025) — el JSON dice
  explícitamente "publicadas… no transcritas".
- **Cataluña — baremos Maresme 2026-2028** (A.9) — sin transcribir celda a celda.
- **Almería / Ourense — `jornadaAnual.horas = "pendiente"`**: bloquea el cálculo
  de horas extra en esas provincias. Verificar si el BOP publica el cómputo anual.
- **Huesca 2025**, y varias tablas "provisionales" que ya tengan definitiva.

## C) MAPEO puesto→nivel desde datos que YA existen (sin BOP nuevo)

El dato salarial está en el corpus, pero faltan las **ocupaciones** (qué puesto =
qué nivel/grupo). Es trabajo de mapeo + verificación, no transcripción:

- ~~**Colectiva por provincia (~296 literales)**~~ — **CERRADO 2026-07-10**: 405
  pares puesto×provincia mapeados con `condicionalPorProvincia` (ver
  `colectiva-ocupaciones-informe-2026-07-10.md`). Quedan DOS huecos nuevos y
  acotados: **Alicante-colectiva** (su anexo solo publica niveles sin nombrar
  ocupaciones → buscar la tabla de encuadramiento en el BOE) y **Lugo-colectiva**
  (tabla multi-columna por tenedores sin capa normalizada).

- **Melilla — 16/16 puestos sin mapear.** Tiene 56 hechos salariales y jornada
  1800 h; el grupo profesional de cada puesto **remite al ALEH** (que está en el
  corpus, `aleh-estatal.json`). Mapeable cruzando ALEH × áreas de Melilla.
- **Huelva (7), Lugo (7), Málaga (5), Álava/Asturias/Granada (4)…** — puestos
  sueltos sin mapear; muchos porque el convenio no tiene esa categoría (honesto) o
  remite al ALEH.

## D) Genuinamente SIN FUENTE completa (necesita el BOP oficial / decisión)

- **Málaga — sección de HOTELES (1y2_hotelesHostales) por niveles**: la tabla
  existe pero la correspondencia puesto→nivel **no se transcribió** y afecta a
  TODOS los puestos de hotel (cocinero, maître…), no solo al maître. Necesita leer
  el BOP oficial de Málaga (clasificación de hoteles). Documentado en la propia
  nota del dato.
- Convenios que **no fijan** precio/recargo de hora extra (Cáceres, Guadalajara,
  Gipuzkoa, Baleares): NO es un hueco de dato — el convenio remite al ET (hora
  extra ≥ ordinaria). Ya lo resuelve el motor. Es duda de interpretación, resuelta
  en `docs/dudas-resueltas/06-investigadas-2026-07-10.md`.

## Prioridad recomendada (por impacto en el "te deben")

1. **Almería/Ourense jornadaAnual** — desbloquea horas extra de 2 provincias.
2. **Melilla mapeo desde ALEH** — desbloquea 1 ciudad entera (16 puestos).
3. **Castellón 2025 definitivas / Huesca 2025** — tablas más recientes.
4. **Málaga hoteles** — necesita el BOP; mientras, hueco honesto.
5. Años futuros (A) — no accionable hasta que se publiquen.
