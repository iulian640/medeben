# Convenios de hostelería

Tablas salariales de los convenios colectivos de hostelería de España,
**transcritas a mano de los boletines oficiales** (BOE y boletines
provinciales/autonómicos) y verificadas antes de entrar aquí.

## Los tres subsectores (importante)

"Hostelería" no es un solo convenio por provincia. Hay **tres subsectores** que
se cruzan con el territorio, y el par (territorio, subsector) determina el
convenio aplicable:

1. **Hospedaje / hoteles** → convenio provincial o autonómico.
2. **Hostelería** (restaurantes, bares, cafeterías) → convenio provincial o
   autonómico **distinto** del de hospedaje (verificado: en Madrid son dos).
3. **Restauración colectiva** (comedores de colegios, hospitales, empresas,
   residencias) → convenio **estatal único** (BOE-A-2025-12598), igual en toda
   España.

Por eso los ficheros se nombran `<territorio>-<subsector>.json`
(p. ej. `madrid-hospedaje.json`).

## Formato

- **Un fichero JSON por convenio**, con su ámbito (territorio + subsector),
  vigencia (años que cubre) y la referencia al boletín oficial de donde se
  transcribió.
- Los textos oficiales no tienen copyright (art. 13 LPI): transcribirlos es legal.

## Reglas

1. **Nada se inventa.** Cada cifra sale de un boletín oficial y se verifica a
   mano antes de mergear. Un dato de convenio inventado o mal copiado puede
   llevar a alguien a reclamar mal.
2. Cada fichero indica su **vigencia** y su **fuente** (boletín, fecha de
   publicación). Si un convenio se actualiza, se añade la tabla nueva sin
   borrar la histórica.
3. **Se aceptan PRs de corrección o de nuevos convenios**: adjunta el enlace
   al boletín oficial que respalda cada cifra.

## Estado

| Convenio | Subsector | Estado |
|----------|-----------|--------|
| `madrid-hospedaje.json` | Hospedaje (hoteles) | ✅ Transcrito del BOCM 121 (23-05-2026), tablas 2025-2028, jornada 1.800 h, nocturnidad y clasificación |
| `madrid-hosteleria.json` | Hostelería (bares/restaurantes) | ✅ Tablas 2025 (sección tercera: comedor+cocina) verificadas VISUALMENTE contra BOCM 82. Jornada 1.800h, nocturnidad 1%/25%. Pendiente: cafeterías, catering, % servicio, varios |

### Aviso: el modelo de datos evoluciona

El convenio de **hospedaje** encajó en un modelo simple (nivel × grupo). El de
**hostelería de Madrid** demuestra que el modelo real debe soportar más ejes:
sistema con/sin porcentaje de servicio, sección y categoría de establecimiento,
área funcional (comedor/cocina/varios) y columnas inicial/garantizado. La
transcripción de convenios complejos debe hacerse con cuidado y verificarse fila
a fila contra el PDF — nunca a ojo ni con prisa.

Pendiente de la primera tanda (por población): Madrid-hostelería (bares/restaurantes),
Cataluña, Valencia, Alicante, Sevilla, y el estatal de restauración colectiva.

**Ojo:** `madrid-hospedaje.json` cubre solo hoteles. Un camarero de un bar de
Madrid va por otro convenio (hostelería), aún sin transcribir.
