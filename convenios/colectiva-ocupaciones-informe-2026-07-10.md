# Mapeo de ocupaciones de la restauración colectiva — informe (2026-07-10)

Cierra el hueco "colectiva por provincia: ~296 literales": hasta hoy `estatal-restauracion-colectiva`
no tenía NINGÚN puesto mapeado y un trabajador de comedor de colegio/hospital/empresa
no podía calcular su mínimo. Ahora **405 pares (puesto × provincia) resuelven a su
fila salarial** y el resto son null documentados (dato ausente > dato erróneo).

## Cómo funciona

Cada anexo provincial del BOE define su tabla con categorías literales propias que
agrupan varias ocupaciones por fila. El mapeo usa la nueva forma `condicionalPorProvincia`:
un árbol cuya pregunta es la provincia y cuya hoja es la categoría literal del anexo
(byte a byte contra la capa normalizada); el motor la resuelve como dimensión `categoria`.
Una provincia en null se poda: ni se ofrece como opción (el usuario cae al modo manual).

## Método (patrón del corpus: 0 discrepancias)

1. **Piloto manual**: Cáceres, validado a mano contra la transcripción (12/16 puestos).
2. **Flota**: 47 provincias, un agente mapeador + un **refutador adversarial** por
   provincia (94 agentes), con los literales exactos de su anexo como única fuente.
3. **Arbitraje manual** de las 42 discrepancias (abajo).
4. **Validación determinista**: los 405 literales casan byte a byte con la capa
   normalizada; `CoberturaSalarioTest` recorre cada hoja hasta un salario
   (0 callejones) y `OcupacionesValidadorTest` vigila la lista curada.

## Criterios fijados (para futuros mapeos)

- **Elisión de prefijo**: "Jefe de recepción / de cocina / …" nombra a Jefe de cocina.
  Es LA convención tipográfica de estas tablas del BOE (el piloto la validó); un
  fragmento "de cocina" jamás es una ocupación por sí solo.
- "Aydte." = Ayudante (abreviatura estándar de los anexos).
- **auxiliar-cocina** acepta filas que nombren pinche, marmitón o fregador/a (así lo
  dice la propia etiqueta curada del puesto). Ayudante y auxiliar NUNCA se cruzan.
- **jefe-sala** acepta "Jefe/a de restaurante o sala", "Maître" y "Jefe/a de comedor".
- **camarera-pisos** exige "camarero/a (de) pisos" nombrado; "Auxiliar de pisos y
  limpieza" NO equivale (solo Cantabria la nombra: 1/48).
- **administrativo** exige la figura propia; "Ayudante/Auxiliar administrativo" son otras.
- Dos filas candidatas con salario distinto = ambigüedad real = null con nota.

## Arbitraje de las 42 discrepancias mapeador↔refutador

- **38 restauradas** (el refutador fue más papista que el criterio): 18 por elisión de
  prefijo, 6 por pinche/marmitón/fregador→auxiliar, 3 por "Jefe de restaurante o sala",
  2 por la abreviatura "Aydte.", y el resto con evidencia estructural caso a caso
  (p. ej. Jaén: el "Administrativo" de la fila 1.40 hereda "Auxiliar" — si no,
  "Administrativo de pisos" sería absurdo — luego el de la 1.45 es el Administrativo real).
- **2 omisiones aceptadas**: Málaga personal-limpieza → "Limpiador/a (jornada entera)"
  (las tablas del corpus son de jornada completa; la fila "media jornada" es la misma
  figura prorrateada) y Pontevedra personal-limpieza → Nivel 9 ("Auxiliar … de limpieza").
- **2 nulls confirmados**: Girona jefe-sala ("sala" en NIV.1 y "de sala" en NIV.3, dos
  salarios) y Lleida personal-limpieza (NIV.4 "Aux. Servicio y Limpieza" vs NIV.5
  "Auxiliar limpieza"). **RE-VERIFICADOS contra el PDF oficial (BOE-A-2025-12598,
  BOE núm. 148 de 20-jun-2025, págs. 82197-82199) el 2026-07-11:** el texto oficial
  NO desambigua (el art. 13 declara las ocupaciones "meramente enunciativas" y remite
  al ALEH, que no mapea a la numeración NIV). Girona duplica LITERALMENTE "Jefe/a de
  sala" en NIV.1 (1.739,97 €) y NIV.3 (1.553,64 €); Lleida son dos oficios distintos
  (servir+limpiar NIV.4 1.282,94 € vs solo limpiar NIV.5 1.231,63 €). Son dudas REALES
  para UGT, no fallos de mapeo → el `null` es correcto. Pregunta exacta para UGT en
  `docs/preguntas-ugt.md` §13.

## Lo NO mapeable hoy (honesto)

| Hueco | Alcance | Qué haría falta |
|---|---|---|
| **Alicante** | 16/16 puestos | Su anexo solo publica "Nivel 1"…"Nivel 6" sin nombrar ocupaciones. Buscar en el BOE la tabla de encuadramiento nivel→ocupación de Alicante (¿otro artículo/anexo del convenio?). |
| **Lugo** | 16/16 puestos | Verificado en la transcripción pero SIN capa normalizada: tabla multi-columna por tenedores (5/4, 3, 1/2). Normalizarla (dimensión extra) y luego mapear. |
| **camarera-pisos** | 47/48 provincias | La figura casi no existe en colectividades (solo Cantabria la nombra). Es coherente con el sector, no un fallo. |
| **barman / sumiller / repostero / jefe-partida** | ~42/48 | Figuras de hostelería clásica que la mayoría de anexos de colectividades no contemplan. Null honesto → modo manual. |
| Girona jefe-sala, Lleida personal-limpieza | 2 pares | Ambigüedad real entre dos filas (ver arbitraje). Consultable a UGT. |

Cobertura por puesto (de 48 provincias): cocinero 39 · jefe-cocina 38 · camarero 38 ·
segundo-jefe-cocina 38 · ayudante-cocina 36 · jefe-sala 36 · personal-limpieza 34 ·
auxiliar-cocina 32 · ayudante-camarero 32 · recepcionista 27 · administrativo 27 ·
jefe-partida 12 · repostero 5 · barman 5 · sumiller 5 · camarera-pisos 1.
