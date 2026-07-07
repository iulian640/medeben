# Esquema obligatorio de un convenio (checklist anti-huecos)

Todo `*-hosteleria.json` (o `-hospedaje`, `-colectiva`) DEBE cubrir estos campos.
Si un dato no está en la fuente, se marca explícitamente `pendiente` o `null` con
nota — **nunca se omite en silencio y nunca se inventa**. Cada dato lleva su
**artículo/anexo** (regla D34) y la `fuente` global lleva boletín + nº + fecha + URL.

Antes de dar por bueno un convenio, pasar esta checklist. Los agentes reciben este
esquema en sus instrucciones.

## 1. Identificación y fuente
- [ ] `id`, `nombre`, `codigoRegcon`
- [ ] `ambitoTerritorial` (tipo, comunidad, provincias) y `ambitoFuncional` (hospedaje / hosteleria / restauracion-colectiva)
- [ ] `fuente`: boletín, número, fecha, documento, **url oficial**
- [ ] `vigencia` (desde/hasta) + nota de ultraactividad si aplica
- [ ] **Es la publicación MÁS RECIENTE** (regla D32). Si hay % pactado sin tabla oficial, NO inventar: dejar el año como `pendiente`.

## 2. Tiempo de trabajo
- [ ] `jornadaAnual.horas` (+ artículo) y horas semanales
- [ ] `descansoEntreJornadasHoras` si el convenio lo modifica (+ artículo)
- [ ] Bolsa de horas / distribución irregular si existe

## 2 bis. VACACIONES — bloque completo (Iulian: "todo lo relativo a ellas")
`vacaciones` con TODO lo que las rodea, cada dato con su artículo:
- [ ] **Días** y si son **naturales o laborables** (¡importante!, 30 naturales ≠ 30 laborables)
- [ ] **Días extra por antigüedad** (algunos convenios suman días con los años)
- [ ] **Cómo se retribuyen**: ¿salario base solo, o promedio con complementos (nocturnidad, festivos...)? Suele ser lo que más se recorta.
- [ ] **Periodo de disfrute** (meses en que se pueden/deben coger; temporada alta bloqueada en hostelería)
- [ ] **¿Las fija la empresa o se pactan?** Preaviso del calendario (normalmente 2 meses)
- [ ] **Fraccionamiento**: ¿se pueden partir? ¿en cuántos periodos? ¿mínimo de días seguidos?
- [ ] **Baja durante vacaciones**: si caes enfermo/accidentado o es maternidad, ¿se interrumpen y se recuperan? (derecho reconocido, poco reclamado)
- [ ] **Festivos dentro de vacaciones**: ¿cuentan o se recuperan?
- [ ] **Vacaciones no disfrutadas** al cesar: se pagan en el finiquito (enlaza con la calculadora de finiquito)

## 3. Retribución
- [ ] `pagasExtraordinarias` (nº — **ojo 14 vs 15** — meses, base, artículo)
- [ ] Tablas salariales por nivel/grupo × categoría de establecimiento, del año vigente (+ anexo). Verificadas en IMAGEN, celda a celda.
- [ ] `nocturnidad`: % o €/hora, **tramo horario exacto** (varía mucho), artículo
- [ ] `horasExtraordinarias`: precio €/hora o fórmula, tope anual, artículo
- [ ] `antiguedad` (escala + base) si existe (+ artículo)
- [ ] `pluses` (transporte, manutención, alojamiento, ropa, quebranto...) con artículo
- [ ] Compensación de festivos trabajados (+ artículo)

## 4. Condiciones no salariales (**D33 — el hueco a cerrar**)
- [ ] `permisosRetribuidos`: matrimonio/pareja, mudanza, fallecimiento/enfermedad grave de familiar, nacimiento, lactancia, exámenes, deber inexcusable... (días por causa + artículo). Anotar si mejora el ET.
- [ ] `complementoIT` (**baja médica**): ¿la empresa complementa la prestación? ¿hasta qué % del salario y durante cuántos días? Distinguir **enfermedad común vs accidente laboral** (+ artículo). ← el dato "oro".
- [ ] Premios (natalidad, nupcialidad, jubilación) si existen (+ artículo)
- [ ] Seguros obligatorios (vida/accidentes: importe + artículo)

## 4 bis. Carrera, contrato y protección (añadido por Iulian)
Cada uno con su artículo:
- [ ] **`ropaTrabajo` / uniforme**: ¿lo da la empresa o hay plus de compensación? importe.
- [ ] **`dietasYKilometraje`**: importe de dieta (comida/cena/pernocta) y €/km por desplazamiento.
- [ ] **`ascensos`**: criterios de promoción (antigüedad, examen, concurso) y cobertura de vacantes.
- [ ] **`periodoPrueba`**: duración por grupo/categoría (clave: durante el periodo de prueba te pueden echar sin indemnización).
- [ ] **`excedencias`**: tipos (voluntaria, forzosa, cuidado de hijos/familiares), duración y derecho de reingreso.
- [ ] **`polivalencia` / categoría**: si haces funciones de categoría superior, ¿te pagan el nivel superior? ¿desde cuándo? (fraude típico: te tienen de ayudante haciendo de cocinero). Enlaza con el comprobador de categoría.
- [ ] **`regimenDisciplinario`**: faltas leves/graves/muy graves y sanciones. Sirve para saber si una sanción o un despido es proporcionado/abusivo.
- [ ] **`formacion`**: horas de formación, permisos para estudios/exámenes, plus de formación.
- [ ] **`contratacion`**: tipos de contrato del sector (fijo-discontinuo es clave en hostelería: llamamiento por orden, derecho a ser llamado cada temporada).
- [ ] **`igualdadYAcoso`**: protocolo de acoso, plan de igualdad, permisos por violencia de género (si el convenio los mejora).

## 5. Metadatos de calidad
- [ ] `estado`: verificado / parcial / pendiente (honesto)
- [ ] `verificadoContra`: qué se leyó en imagen y qué no
- [ ] `fechaVerificacion`

---

**Regla de oro:** un dato erróneo es peor que un dato ausente. Ante la duda,
`pendiente` + nota + duda para UGT. Verificación = leer la IMAGEN del PDF oficial,
no la extracción de texto (desalinea columnas).
