# Estado y vigencia de los convenios — CORPUS COMPLETO (2026-07-08)

Control de currency: cada convenio está en su **última publicación** con tablas (regla D32).
**55 convenios**, todos transcritos de la imagen oficial celda a celda y con **spot-check independiente OK (0 discrepancias)**.

## Resumen
- **Cobertura territorial 100%** (50 provincias + Ceuta + Melilla + Madrid×2 + Gipuzkoa/La Rioja hospedaje + ALEH estatal + estatal colectiva con 49 anexos). Ver `INDICE.md`.
- Verificación: cada convenio lo transcribió un agente leyendo la imagen del PDF, y **otro agente distinto** lo re-verificó contra la fuente oficial (spot-check). Todos OK.
- Método (D32 + no inventar): siempre la publicación más reciente con tablas; dato ausente > dato erróneo; lo no publicado → `pendiente`.

## Correcciones aplicadas durante la verificación
- **estatal-restauracion-colectiva**: `nocturnidadPct: 25` estaba sobregeneralizado (era la cláusula de Cantabria, no norma nacional). Corregido a regla por provincia con redacción literal de cada una (art. 22, "Jornada especialidades" del BOE).
- **Alicante**: tenía la tabla 2025 → actualizado a la 2026 (BOP nº29, 12-02-2026) + backfill de todo el bloque de condiciones (era pre-ESQUEMA).
- **Madrid-hostelería**: faltaban 6 categorías de cocina → completadas.
- **Madrid-hospedaje**: `$comment` "BORRADOR/NO cargar" desfasado → limpiado (ya está verificado).

## Años en ultraactividad / `pendiente` (vigilar)
Varias provincias aplican por ultraactividad la última tabla publicada porque el convenio venció y no hay nuevo (p.ej. Murcia 2024, Teruel 2023 —negociación suspendida—, León/Lugo/La Rioja/Navarra/Cantabria tablas 2024-2025). Los años sin tabla oficial están marcados `pendiente`, no inventados. Revisar anualmente.

## Periodo de prueba (días de prueba)
- 10 convenios lo fijan con tabla propia (Álava, Asturias, Burgos, Cantabria, Gipuzkoa, Lleida, Lugo, Navarra, Salamanca, Toledo).
- 37 remiten al ALEH → resueltos con `aleh-estatal.json` (tabla 90/60/45 por grupo × tipo de contrato, art. 21 ALEH VI).
- Los 5 huecos (Alicante, Málaga, Madrid×2, colectiva) cerrados: capturan su dato o su remisión explícita.

## Pendiente de mantenimiento (no bloquea nada)
- **Modificación 2026 del ALEH** (audiencia previa antes del despido, olas de calor, LGTBI): firmada abr-2026, **aún sin publicar en BOE** → integrar en `aleh-estatal.json` cuando salga (campo `modificacion2026Pendiente`).
- Dudas UGT menores anotadas en cada JSON (la recurrente: retribución de vacaciones base-vs-promedio).

## Lección aprendida (2026-07-07, se mantiene la regla)
El primer PDF de Málaga (BOP 90, may-2025) traía 2024 definitivas + 2025 **provisionales**; estando en 2026 eso está desfasado. **Antes de transcribir, buscar la publicación más reciente de las tablas.** Un dato viejo es un dato erróneo.
