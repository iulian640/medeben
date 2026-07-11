# Dudas resueltas por investigación (2026-07-11)

> Cierra varias preguntas que estaban en `preguntas-ugt.md` marcadas
> [REQUIERE UGT], verificándolas contra el TEXTO OFICIAL. Regla de oro: nada se
> da por resuelto sin cita literal + URL; lo que el texto no fija, sigue en UGT.

## §4 — Nocturnidad Madrid 22:00-00:00 al 1% → CONFIRMADO CORRECTO

La transcripción de `madrid-hosteleria.json` (1% de 22:00 a 00:00, 25% de 00:00
a 08:00, fórmula `salarioBase × pct / (4×40)`) **coincide celda a celda** con el
Art. 27 del convenio. Legalmente vale (art. 36.2 ET no fija mínimo de
nocturnidad). **Nada que cambiar.**
Fuente: BOCM nº82, 06-04-2024, BOCM-20240406-2, Art. 27.

## §5 — "Salario inicial" vs "garantizado" → RESUELTO (el motor ya es correcto)

Las columnas *inicial* y *garantizado* SOLO existen en el **Anexo III**, cuyo
encabezado literal es "TABLAS SALARIALES EXCLUSIVAMENTE PARA AQUELLAS EMPRESAS
QUE MANTENGAN EL SISTEMA DE PARTICIPACIÓN DE PORCENTAJE DE SERVICIO" (sistema
residual desde 2004, **fuera de alcance v1**). El régimen general (99% de
empresas) es el **Anexo I**: una única cifra de salario base por nivel y clase.
El motor deriva su `salarioBase` exclusivamente del Anexo I → **es correcto**.

- La BASE de nocturnidad y hora extra es "el salario base reflejado en las tablas"
  (Art. 27 y Art. 25) = el Anexo I. El motor ya la usa; su fórmula calca al convenio.
- El *garantizado* (cuando aplica el sistema legacy) es el salario TOTAL
  garantizado (suelo), no el *inicial* (Disp. Transitoria Primera B.a).

**Aplicado:** en `madrid-hosteleria.json`, la etiqueta informativa
`salarioGarantizadoMensual` de `horasExtraordinarias.valorHoraOrdinaria` se
renombró a `salarioBaseMensual` (string que el motor no lee; era confuso).
Residuo (solo si el Anexo III entrara en alcance): confirmar con UGT si en el
sistema de % de servicio la nocturnidad se calcula sobre inicial o garantizado.
Fuente: BOCM-20240406-2, Art. 25, Art. 27, Disp. Trans. Primera B.a, Anexo III (encabezado).

## §5b — Cafeterías: 2 tazas > 3 tazas en el "fijo" → RESUELTO (no es error de dato)

La anomalía vive SOLO en la columna "sueldo inicial" del Anexo III (el fijo del
sistema de % de servicio): una cafetería de 3 tazas genera más % de servicio, así
que su fijo se pone más bajo, pero el **total garantizado es mayor**. En el
régimen general (Anexo I), la cafetería se clasifica por tazas en clase de empresa
(3→A, 2→B, 1→C) y los importes SÍ son monótonos. El motor usa el Anexo I → **es
correcto**; nunca debe tratar el "sueldo inicial" como salario.

**Aplicado:** corregido un comentario FALSO en `madrid-hosteleria.json`
(`seccionCuartaCafeterias.$comment`) que afirmaba que el "cuadro sin servicio de
sala" (tabla garantizado) no aparece como tabla independiente — sí aparece para
2023 (pág. 51) y 2024 (pág. 58); solo se omite en 2025 (pág. 64, probable errata).
Fuente: BOCM-20240406-2, Anexo III, Nota 2 y págs. 50-51 / 57-58 / 64.

## §12 Valladolid — Jefe de Partida NS II vs NS III → SIGUE NECESITANDO UGT

Verificado que **no hay corrección de erratas publicada**: la corrección del BOP
(nº2025/132, 14-07-2025) solo tocó el Anexo II (manutención) y añadió el Anexo V
(LGTBI); la tabla única 2026 sigue listando "Jefe de Partida" a la vez en NS II
(1.322,96 €/mes) y NS III (1.276,03 €/mes). El ALEH no fija el nivel salarial
(remite al art. 26.3 ET). Es interpretación de comisión paritaria / juzgado
(≈47 €/mes, ~657 €/año a 14 pagas), así que **elegir uno sería inventar** → duda
real para UGT (pregunta en `preguntas-ugt.md §12`). Sub-duda "administrativo":
por el nombre de categoría del propio convenio mapea a "Administrativo/a" NS IV
(Área Primera), salvo que las funciones reales digan otra cosa (art. 22 ET).

## Estado de lo firmado sin publicar (Nivel 1 de revisiones-pendientes)

- **La Rioja HOSPEDAJE 2026-2028 → YA PUBLICADO** (BOR nº127, 07-07-2026,
  Resolución 544/2026; suscrito 24-06-2026). Pendiente de **transcribir** (tablas
  del Anexo II con verificación en imagen). Ver `revisiones-pendientes.md`.
- **Modificación 2026 del VI ALEH → SIGUE SIN PUBLICAR** en el BOE (verificado hoy).
- **Permiso por fallecimiento a 10 días (art. 37.3 ET) → SIGUE SIN PUBLICAR** en el BOE.
