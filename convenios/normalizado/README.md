# Capa derivada normalizada

Las transcripciones (`../<id>.json`) son la **fuente de verdad**: espejo del boletín
oficial, verificadas celda a celda contra la imagen del PDF. Esta carpeta contiene
la **capa derivada**: los mismos números reorganizados en *hechos* planos con forma
única para todos los convenios. Es lo único que lee el motor de cálculo.

Piénsalo como código fuente (transcripción) vs compilado (esta capa).

## Formato

Un fichero por convenio, mismo nombre que su transcripción: `<id>.json`.

```json
{
  "id": "madrid-hosteleria",
  "hechos": [
    {
      "concepto": "salarioBase",
      "dimensiones": { "tabla": "general", "nivel": "III", "claseEmpresa": "B" },
      "desde": "2025-01-01",
      "hasta": "2025-12-31",
      "importe": 1250.91,
      "unidad": "EUR/mes",
      "articulo": "Anexo I C) c)",
      "rutaCruda": "/tablasAnexoI/salariosBaseMensuales/2025/general/III/B"
    }
  ]
}
```

- **concepto** — qué es el dato. Hoy: `salarioBase` (todos los convenios) y, donde el
  convenio los publica por dimensión en vez de en sus nodos propios, `jornadaAnual` y
  `mensualidadesEquivalentes` (la colectiva los trae por provincia, issue #231). Vendrán
  más: pluses, precios de hora extra...
- **dimensiones** — pares clave/valor propios de cada convenio (nivel, clase de empresa,
  categoría de establecimiento...). Las claves y valores usan los mismos nombres que la transcripción.
- **desde / hasta** — vigencia del dato como rango de fechas. Esto absorbe las rarezas:
  los periodos abril→marzo de Baleares son solo un rango más.
- **importe** — número, nunca string.
- **articulo** — cita de origen (regla D34: cada dato con su artículo).
- **rutaCruda** — JSON Pointer (RFC 6901) a la celda exacta de la transcripción de la
  que sale el importe. Es la procedencia del dato.

## Reglas

1. **NUNCA se edita un importe aquí.** Si un número está mal, se corrige la
   transcripción (verificando contra el PDF oficial) y se re-deriva este fichero.
2. **`rutaCruda` es obligatoria** en cada hecho. El validador del backend
   (`CapaNormalizadaValidadorTest`) resuelve cada puntero contra la transcripción y
   falla el build si un importe no coincide: la duplicación no puede divergir en silencio.
3. No puede haber dos hechos con el mismo concepto y dimensiones cuyas vigencias se solapen.
4. Dato ausente en la transcripción (marcado `pendiente`) = hecho que NO existe aquí.
   Nunca se inventa ni se extrapola.

## Estado de derivación

**54 de 55 convenios derivados** (2026-07-08), ~8.760 hechos `salarioBase`, todos con
procedencia verificada. El único sin fichero es `aleh-estatal`: es un acuerdo marco
(clasificación, periodo de prueba, disciplinario) sin tablas salariales — correcto que no exista.
Además (2026-07-11, issue #231), `estatal-restauracion-colectiva` tiene 49 hechos
`jornadaAnual` (1.800 h del marco nacional, materializada por provincia) y 40
`mensualidadesEquivalentes` (pagas verificadas provincia a provincia contra el crudo).

Huecos conocidos dentro de ficheros derivados (documentados en el `$comment` de cada uno):

- **estatal-restauracion-colectiva**: Lugo sin derivar (la transcripción trae esa provincia
  como strings multi-columna, sin celdas numéricas para `rutaCruda` — re-transcribir algún día)
  y 1 celda de Córdoba con errata del propio BOE (`"1.21,73"`).
- **estatal-restauracion-colectiva, pagas**: 9 provincias sin estructura de pagas en su
  anexo (Granada, Santa Cruz de Tenerife, Barcelona, Girona, Islas Baleares, Alicante,
  Valladolid, Lleida, Tarragona) = sin hecho `mensualidadesEquivalentes` → horas extra
  en 422 honesto hasta que el BOE las publique. Las pagas de cuantía fija (octubre de
  Zaragoza, Santa Marta de Asturias, septiembre de Guadalajara, paga del sector de
  Burgos...), las parciales (15 días de Córdoba, 16 de Cantabria, Santa Marta de Lugo)
  y las condicionadas no entran en el multiplicador (pendiente conocido del motor).
- **Regímenes especiales fuera de alcance v1**: Anexo III de madrid-hosteleria (% de servicio
  histórico), Anexo IV de cadiz (casinos), Anexo II BIS de valencia (sin experiencia),
  baremos de cafeterías de cataluna.
- **Años `pendiente` en la transcripción = sin hechos** (teruel 2024-26, murcia 2025+,
  huesca 2025-26...): el motor aplica la última tabla publicada por ultraactividad.
- Tablas **provisionales** publicadas (badajoz 2026, guadalajara 2026, albacete jul-dic 2025,
  salamanca 2026) sí están derivadas, marcadas en su `articulo`/`$comment` — re-derivar
  cuando salga la definitiva (regla D32).
- **melilla**: 16 celdas "S.M.I." sin hecho (retribución = SMI legal, no cifra del convenio);
  **murcia**: el plus SMI va aparte; **cuenca** publica en EUR/año (ver `unidad`).

Regla de vigencias: la capa deriva el rango que publica el boletín (año natural, periodos
abr→mar, temporadas jul→jun...); NUNCA extiende por ultraactividad — eso lo resuelve el motor.
