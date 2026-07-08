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

- **concepto** — qué es el dato (`salarioBase` por ahora; vendrán más: pluses, precios de hora extra...).
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

| Convenio | Conceptos derivados |
|---|---|
| madrid-hosteleria | salarioBase (Anexo I general + catering, 2023-2025). Anexo III (% servicio histórico) fuera de alcance: solo empresas que lo conservan. |
| baleares-hosteleria | salarioBase (Anexos I-II, nivel × categoría, periodos abr-mar 2025-2028) |

Los 53 restantes: pendientes de derivar (proceso mecánico por agentes, la transcripción manda).
