# Dudas resueltas — GRUPO D

Resolución con **fuentes oficiales** de las dudas registradas (`dudasUGT` /
`dudasParaUGT` / `pendienteCondiciones` / `modificacion2026Pendiente`) en los 13
convenios del grupo D:

Segovia · Sevilla · Soria · Tenerife · Teruel · Toledo · Valencia · Valladolid ·
Vizcaya · Zamora · Zaragoza · ALEH estatal · Estatal restauración colectiva.

**Regla de oro:** ninguna respuesta inventada. Cada punto lleva su fuente
(artículo del convenio, del Estatuto de los Trabajadores —ET—, del VI ALEH, del
BOE/boletín o doctrina del Tribunal Supremo). Si de verdad depende de la empresa o
no hay fuente pública, se dice explícitamente.

Fecha de resolución: **2026-07-08**.

---

## 🔎 Titular del grupo — ¿el ALEH 2026 ya salió en el BOE?

**NO.** Verificado el 2026-07-08 en `boe.es`: la última publicación del Acuerdo
Laboral estatal de Hostelería sigue siendo el **VI ALEH** (BOE-A-2023-6344,
10-03-2023) con su corrección de errores (BOE-A-2023-10527, 19-04-2023). **No
existe ninguna disposición de 2026** que registre la modificación firmada el
13-04-2026 (audiencia previa al despido, olas de calor, LGTBI). Fuente doctrinal
independiente que lo confirma: INEAF, *«VI ALEH 2026»* — «la modificación del VI
ALEH 2026 está **pendiente de publicación oficial en el BOE**, aunque sus efectos
se aplican desde la firma del acuerdo».

➡️ **Conclusión:** el campo `modificacion2026Pendiente` del fichero
`aleh-estatal.json` sigue siendo correcto. Por la regla de oro, **NO se incorpora
al articulado** hasta que aparezca el texto oficial en el BOE. **No hay “⚠️
ACTUALIZAR CONVENIO” en este grupo.** (Hay que seguir vigilando el BOE: cuando
salga, sí tocará actualizar `regimenDisciplinario.procedimiento`.)

---

## Dudas transversales (recurrentes en varios convenios)

Estas dudas se repiten en casi todo el grupo. Respuesta única aquí; en cada
convenio se remite a **«ver 00-transversales.md»** (documento canónico pendiente
de crear) más esta ficha.

### T1 · Retribución de las vacaciones (¿salario base o promedio?)
Aunque el convenio no lo diga, la vacación se paga con la **retribución normal o
media**, no solo el salario base. Los complementos que se cobran de forma
**habitual y ordinaria** (nocturnidad, festivos habituales, etc.) entran en la
paga de vacaciones.
- **Fuente:** art. 38 ET; art. 7.1 del **Convenio nº 132 OIT**; doctrina
  consolidada del **Tribunal Supremo, Sala 4ª** (p. ej. STS 8-6-2016 y
  posteriores) y **STJUE C-539/12 (Lock)**. Qué complementos son «habituales» en
  cada empresa → confirmar con UGT.

### T2 · Tipo de días de vacaciones (naturales vs laborables)
Si el convenio dice «30/31 días» sin especificar, se entienden **días
naturales** (el mínimo legal del art. 38.1 ET son 30 días naturales).
- **Fuente:** art. 38.1 ET.

### T3 · Periodo de prueba
Cuando el convenio provincial no fija duraciones propias, rigen las del **VI
ALEH** (BOE-A-2023-6344) y, subsidiariamente, el **art. 14 ET**. No se
transcriben cifras aquí para no inventar: tomar las del ALEH.
- **Fuente:** VI ALEH (BOE-A-2023-6344); art. 14 ET.

### T4 · Régimen disciplinario (faltas y sanciones)
Materia **reservada a la negociación estatal**: la fija el **VI ALEH**
(BOE-A-2023-6344), no el convenio provincial. El ET (art. 58) es el marco.
⚠️ Ojo: la modificación 2026 (pendiente de BOE, ver titular) añadirá la
**audiencia previa** al despido disciplinario, alineada con la **STS Sala 4ª
18-11-2024**.
- **Fuente:** VI ALEH (BOE-A-2023-6344), art. 10.7 y capítulo disciplinario; art.
  58 ET.

### T5 · Representación sindical / crédito horario / garantías
Cuando el convenio no mejora ni detalla, rige el marco legal general.
- **Fuente:** arts. 62-68 ET (representación unitaria y crédito horario) y **LOLS
  (LO 11/1985)**, art. 10 (secciones y delegados sindicales).

### T6 · Compensación en metálico de la ropa/uniforme sin importe
Si el convenio obliga a dar el uniforme o compensarlo en metálico **pero no fija
el importe**, no hay fuente pública que lo cuantifique: **depende del acuerdo de
empresa / comisión paritaria**. Duda real para UGT.

---

## Convenios provinciales

### Segovia — `segovia-hosteleria.json`
Convenio 2022-2026 (BOP Segovia nº29, 08-03-2023), en ultraactividad plena
(Art. 3).

- **Retribución de vacaciones (Art. 9 no lo dice):** ver **T1**. → UGT confirma
  qué complementos son habituales en Segovia.
- **Importe compensación ropa en metálico (Art. 22):** ver **T6**. No hay fuente
  pública → UGT/empresa.
- **Erratas del BOP en «salario año» 2025 (18.376,73 vs 18.346,72):** el ET no
  regula la casilla «salario año» (es informativa). El importe que se abona es el
  **salario mensual × 14 pagas** (Art. 15 del propio convenio: 14 pagas). El
  «salario año» correcto es el que cuadre con esa multiplicación. → Confirmar con
  UGT cuál casilla usa la Oficina de Trabajo como oficial.
- **Periodo de prueba:** ver **T3** (el convenio remite al ALEH; solo excluye
  periodo de prueba en subrogación de colectividades).
- **Alojamiento 15 €/mes (Art. 19) sin actualización:** el convenio fija ese
  importe y **no lista incrementos**, luego mientras rija este texto sigue siendo
  15 €/mes (Art. 19). No hay tabla que lo actualice → así consta en el BOP. Duda
  menor para UGT (¿uso de empresa distinto?).
- **¿Se negocia ya el convenio 2027?** No consta publicación en el BOP a
  julio-2026; rige la **ultraactividad plena del Art. 3**. → UGT informa del
  estado de la mesa.

### Sevilla — `sevilla-hosteleria.json`
Convenio 2025-2028 (BOP Sevilla nº102, 30-05-2025), **vigente y actual**.

- **Código REGCON:** dato administrativo no incluido en el articulado leído; se
  consulta en el registro REGCON del Ministerio / Junta de Andalucía. No es una
  duda de interpretación.
- **Régimen disciplinario:** ver **T4** (lo regula el VI ALEH).
- **Permisos retribuidos (matrimonio, mudanza, fallecimiento, nacimiento,
  lactancia, exámenes):** el convenio remite al **art. 37 ET**; el convenio solo
  mejora bautizo/comunión/boda. → aplicar art. 37 ET (redacción vigente tras el
  RDL 5/2023: p. ej. 5 días por accidente/enfermedad grave/hospitalización de
  familiares hasta 2º grado o convivientes).
- **Periodo de prueba, excedencias, ascensos, polivalencia:** ver **T3**;
  excedencias → art. 46 ET; ascensos/promoción → art. 24-25 ET y VI ALEH.
- **Vacaciones (fraccionamiento, baja durante vacaciones, no disfrutadas al
  cesar):** art. 38 ET — fraccionamiento por acuerdo; baja durante vacaciones →
  interrupción (art. 38.3 ET); no disfrutadas al cesar → se **compensan
  económicamente** en el finiquito (doctrina TS: solo se compensan en dinero al
  extinguirse el contrato).
- **Uniforme en metálico:** el convenio lo entrega **en especie**, sin importe →
  no procede plus (fuente: el propio convenio).
- **Dietas y kilometraje:** no aparecen en el convenio → no hay fuente
  provincial; se rige por acuerdo de empresa / art. 40 ET si hay desplazamiento.
  Duda para UGT.

### Soria — `soria-hosteleria.json`
Convenio 2022-2025 (articulado BOP Soria nº103, 07-09-2022; **tablas definitivas
2025** en BOP Soria nº18, 11-02-2026), en ultraactividad.

- **Vacaciones «31 días» sin especificar (Art. 7):** ver **T2** → naturales
  (art. 38.1 ET).
- **Retribución de vacaciones:** ver **T1**.
- **Horas extra — divisor 1.785 h desactualizado (Art. 25):** el convenio fijó la
  fórmula con la jornada 2022-2024; para 2025 la jornada baja a 1.780 h y **el
  texto no actualizó el divisor**. No hay fuente oficial que corrija la errata →
  es una **discrepancia real del convenio para la comisión paritaria / UGT**.
  Criterio legal de mínimos: el valor de la hora extra no puede ser inferior al
  de la ordinaria (art. 35.1 ET).
- **Periodo de prueba:** ver **T3**.
- **Fijo-discontinuo (llamamiento/orden):** art. **16 ET** y VI ALEH.
- **Crédito horario:** ver **T5** (arts. 62-68 ET, LOLS).
- **Dietas y kilometraje:** no regulados (solo plus de distancia) → sin fuente
  provincial; acuerdo de empresa. Duda para UGT.

### Tenerife — `tenerife-hosteleria.json`
Convenio 2025-2028 (articulado BOP S/C Tenerife nº154, 22-12-2025; tablas
corregidas en BOP nº4, 09-01-2026), **vigente y actual**. Dudas casi todas de
**transcripción de tablas oficiales** → requieren cotejo con la imagen del BOP o
confirmación de UGT; no hay fuente externa que las resuelva.

- **Complemento IT cuando hay sustitución (Art. 30) — redacción confusa:**
  requiere interpretación de la **comisión paritaria / UGT**; el articulado es la
  única fuente y es ambiguo. No se puede resolver sin aclaración oficial.
- **Manutención: 293,04 €/año (tabla) vs 293,16 €/año (Art. 48):** discrepancia
  interna del propio BOP. Regla general: **prevalece la tabla salarial** (anexo
  numérico) frente al redondeo del articulado, pero → confirmar con UGT cuál
  aplica la Oficina de Trabajo.
- **Celdas a 0,00 € (AF5 «Encargado/a sección», «Especialista mantenimiento…»):**
  posible error de la tabla oficial → **verificar contra la imagen del BOP nº4** /
  UGT. No inventar importe.
- **Matrimonio 15 días (Art. 28.1.1) — ¿naturales o laborables?** ver **T2** →
  naturales por defecto (art. 38/37 ET).
- **Celda «#¡VALOR!» (Camping 2ª, grupo 2B, Titulados):** error de fórmula en la
  hoja oficial del BOP → dejado `null`; el importe correcto solo lo puede dar la
  **corrección de errores del BOP / UGT**. No inventar.
- **Grupo 3C ≥ 3B y 3A < 3B en barman (AF3):** transcrito tal cual de la hoja
  oficial; **confirmar con UGT** si es intencionado o errata.
- **Filas de mantenimiento a 0,00 €/en blanco:** mismo patrón → verificar con la
  imagen oficial / UGT.

### Teruel — `teruel-hosteleria.json`
Convenio 2022-2023 (BOP TE nº200, 19-10-2023), **en ultraactividad** desde que la
patronal bloqueó la mesa el 05-07-2024.

- **Tablas 2024/2025/2026 no publicadas — ¿salió alguna revisada?** Verificado a
  julio-2026: **no consta ninguna tabla nueva** publicada en el BOP TE; el
  convenio sigue en ultraactividad con las tablas del texto 2022-2023 (fuentes
  agregadoras coinciden: no hay nuevo convenio ni tablas). El art. 28 preveía una
  revisión IPC 2024 que debía fijar el nuevo convenio **nunca firmado**. → UGT
  confirma si hubo alguna tabla registrada que no esté en el BOP.
- **Vacaciones «30 días» (quincenas naturales):** ver **T2** → naturales.
- **Ropa de trabajo sin importe del plus:** ver **T6** → UGT/empresa.
- **Régimen disciplinario:** ver **T4** (VI ALEH).
- **Periodo de prueba:** ver **T3** (VI ALEH).
- **Representación sindical (elección, garantías/fuero):** ver **T5** (arts.
  62-68 ET, LOLS).
- **Plus fidelidad — solo consta 2022 (72,07 €):** el de 2023 se revaloriza por
  IPC pero **no está publicado** → no inventar; UGT confirma importe.

### Toledo — `toledo-hosteleria.json`
Convenio 2022-2024 **prorrogado a 2025** (BOP Toledo nº205, 27-10-2025); a
julio-2026 sin tablas 2026, en ultraactividad con tablas jul-dic 2025.

- **Preaviso del calendario de vacaciones (Art. 33 no lo fija):** por defecto se
  conoce con **al menos 2 meses** de antelación. **Fuente:** art. 38.3 ET.
- **IT durante las vacaciones (no regulado en el Art. 33):** aplica el **art. 38.3
  ET** — si la IT (incluida contingencia común) coincide con las vacaciones, se
  **interrumpen** y se disfrutan después (respaldado por STJUE C-78/11 ANGED y la
  reforma del art. 38.3 ET). Sí, el derecho existe aunque el convenio calle.
- **Nocturnidad (Art. 43) — exige 2/3 de jornada nocturna Y ciclo de 24 h:** es
  una **condición del propio convenio**; cómo se aplica a bares/discotecas de
  temporada sin ciclo de 24 h es interpretación de la comisión paritaria. → Duda
  real para UGT (parece dejar fuera mucha nocturnidad efectiva). Suelo legal: el
  trabajo nocturno es el realizado entre las **22:00 y las 06:00** (art. 36.1 ET).
- **Seguro de accidentes 17.000 € (Art. 48) del texto 2022:** el convenio no lo
  actualiza en las tablas anuales → mientras rija este texto sigue siendo 17.000 €
  (Art. 48). ¿Póliza mejorada? → UGT/empresa.
- **Régimen disciplinario:** ver **T4** (Art. 51 remite al ALEH).
- **Excedencia voluntaria / sindical:** art. **46 ET** y LOLS.
- **Representación sindical:** ver **T5**.

### Valencia — `valencia-hosteleria.json`
XVII Convenio 2022-2025 (BOP Valencia nº26, 07-02-2023), **en ultraactividad**
desde 01-01-2026 (ET art. 86.3); mesa del nuevo convenio abierta en 2026.

- **Retribución de vacaciones (Art. 15 no lo concreta):** ver **T1**.
- **Periodo de prueba:** ver **T3** (el provincial solo remite al V/VI ALEH).
- **Régimen disciplinario:** ver **T4** (versión vigente = VI ALEH).
- **Manutención — 25 € en especie (Art. 20, a efectos de cotización) vs
  complemento del Anexo IV (41,32-44,13 €/mes 2025):** son **dos conceptos
  distintos**: el de 25 € es la *valoración en especie* para cotizar a la SS (no
  es dinero que se cobra); el del Anexo IV es el **complemento salarial** que sí
  se abona. Para el cálculo «te deben» → usar el **complemento del Anexo IV**.
  → Confirmar con UGT que no se solapan.
- **Anexo II BIS (ingreso especial sin experiencia):** no es duda legal sino de
  **diseño de la app** — sí, conviene manejar esas tablas reducidas para nuevos
  ingresos. (Nota: la comisión paritaria ya emitió actas interpretativas —
  Acta 1/2025 sobre art. 22 IT, BOP nº215, 10-11-2025 — útiles como fuente.)

### Valladolid — `valladolid-hosteleria.json`
Convenio 2018-2026 (BOP nº2021/247, 28-12-2021), con corrección BOP nº2025/132
(14-07-2025); ultraactividad pactada de articulado y tablas 2026.

- **Retribución de vacaciones (Art. 6):** ver **T1**.
- **Fraccionamiento de vacaciones:** art. 38 ET (por acuerdo; garantía de 15 días
  estivales si hay cierre, ya en el convenio).
- **Periodo de prueba:** ver **T3** (ALEH + art. 14 ET).
- **Representación sindical (elección, garantías art. 68 ET):** ver **T5**.
- **Régimen disciplinario (Arts. 37-44):** aunque el convenio lo regula, el
  **acta de 2025 (BOP nº2025/132) lo declara materia reservada a la negociación
  estatal (art. 10.7 VI ALEH)** → prevalece el VI ALEH; vigilar la sustitución
  formal. **Fuente:** acta 25-06-2025 + art. 10.7 VI ALEH. Ver **T4**.

### Vizcaya — `vizcaya-hosteleria.json`
VII Convenio 2021-2027 (tablas 2026 en BOB nº38, 25-02-2026), **vigente y
actual**. Varias dudas son de **cotejo de tablas contra la imagen del PDF**.

- **Matriz de permisos por fallecimiento/enfermedad grave (Art. 14):** columnas
  desalineadas en la extracción → **verificar celda a celda contra la imagen del
  BOB**. El suelo legal es el art. 37.3 ET (redacción RDL 5/2023). No inventar.
- **Escala de antigüedad (Art. 19):** tabla desalineada → verificar contra
  imagen oficial del BOB. No inventar porcentajes.
- **Tramo nocturno (Art. 22 no lo explicita):** por defecto **22:00-06:00**.
  **Fuente:** art. 36.1 ET.
- **Pluses 2026 no indexados (manutención, desgaste herramientas, servicios
  extraordinarios):** el articulado da cifras 2025; confirmar la revisión 2026 en
  las **tablas del BOB nº38 (25-02-2026)** o con UGT. No inventar.
- **Régimen disciplinario / grupos profesionales:** ver **T4** (VI ALEH).
- **Periodo de prueba:** ver **T3**.
- **Tablas 2027:** aún no publicadas a julio-2026; revisión pactada IPC 2026 +
  1,6 puntos (Art. 6) → pendiente. No inventar.

### Zamora — `zamora-hosteleria.json`
Convenio 2020-2026 (BOP Zamora nº67, 08-06-2022), ultraactividad total.

- **Jornada anual (Art. 16 solo fija 40 h/semana):** el ET no impone un cómputo
  anual cerrado; el límite es **40 h semanales de promedio en cómputo anual**
  (art. 34.1 ET). Sin más detalle en el convenio, la jornada anual se deriva de
  las 40 h/semana menos festivos y vacaciones. → UGT confirma el cómputo aplicado.
- **Retribución de vacaciones (Art. 19):** ver **T1**.
- **Horas extraordinarias — sin precio/recargo (Art. 8 remite al ET, tope 80
  h/año):** el valor de la hora extra **no puede ser inferior al de la hora
  ordinaria** (o compensarse por descanso). **Fuente:** art. 35.1 y 35.2 ET.
- **Complemento IT — ¿desde qué día el 100 %? (Art. 23):** el convenio no lo
  indica → duda menor de interpretación para UGT; sin fuente que lo precise.
- **Régimen disciplinario:** ver **T4** (VI ALEH).
- **Periodo de prueba:** ver **T3** (ALEH/ET).
- **Representación sindical (Art. 28 remite a ET/LOLS):** ver **T5**.

### Zaragoza — `zaragoza-hosteleria.json`
Convenio 2023-2025 (BOPZ nº84, 13-04-2024), **en ultraactividad** desde 2026.

- **Revisión salarial 2026 / tablas 2026 (Art. 43, cláusula ligada a datos
  INE):** verificado a julio-2026: **no hay tablas 2026 publicadas en el BOPZ**.
  El convenio sigue en ultraactividad y se aplican las **tablas de 2025** hasta
  que se firme el nuevo convenio o la comisión paritaria active la revisión (que
  no es un incremento cerrado: +0,50 % por cada indicador INE —pernoctaciones y
  viajeros 2025 > 2023— con efectos desde 01-01-2026). **No se inventan importes
  2026.** → UGT/comisión paritaria informan si ya se calculó la revisión.

---

## Convenios estatales

### ALEH estatal — `aleh-estatal.json`
VI ALEH (BOE-A-2023-6344, 10-03-2023; corrección BOE-A-2023-10527).

- **Modificación 2026 (audiencia previa al despido, olas de calor, LGTBI),
  firmada 13-04-2026:** **NO publicada en el BOE a julio-2026** (ver titular al
  inicio del documento). Por la regla de oro **no se incorpora** hasta tener el
  texto oficial. El campo `modificacion2026Pendiente` es correcto. Vigilar el
  BOE; cuando salga, actualizar `regimenDisciplinario.procedimiento` (audiencia
  previa, 2 días para responer, alineado con **STS Sala 4ª 18-11-2024**) y
  `fuente`/`vigencia`. **No procede “⚠️ ACTUALIZAR CONVENIO” todavía.**

### Estatal restauración colectiva — `estatal-restauracion-colectiva.json`
Convenio estatal de restauración colectiva 2025 (BOE-A-2025-12598, 20-06-2025).

- **¿Prórroga/ultraactividad para 2026?** El convenio tenía vigencia **solo hasta
  31-12-2025** y quedó **denunciado automáticamente** al publicarse. A julio-2026
  **no consta en el BOE un nuevo convenio ni tablas 2026** de restauración
  colectiva (ojo: BOE-A-2026-2224 es «marcas de restauración moderna», convenio
  **distinto**). Por tanto: **está en ultraactividad (art. 86.3 ET)**, se
  mantienen las condiciones y las tablas 2025. El propio texto prevé un mecanismo
  de incremento 2026 (analizar en el 1er semestre de 2025 un incremento a cuenta y
  ajustar por desviación del IPC real 2025), **pendiente de acordar y publicar**.
  → UGT confirma si la mesa ya pactó el incremento 2026. No inventar tablas 2026.

---

## Resumen operativo

- **Resueltas con fuente oficial (ET / VI ALEH / boletín / TS):** retribución de
  vacaciones (T1), tipo de días (T2), periodo de prueba (T3), régimen
  disciplinario (T4), representación sindical (T5), preaviso e IT en vacaciones
  (Toledo, art. 38.3 ET), horas extra (Soria/Zamora, art. 35 ET), tramo nocturno
  (Toledo/Vizcaya, art. 36.1 ET), permisos (Sevilla, art. 37 ET), excedencias
  (art. 46 ET), fijo-discontinuo (art. 16 ET), jerarquía disciplinaria de
  Valladolid (art. 10.7 VI ALEH).
- **Requieren UGT / empresa / comisión paritaria (sin fuente pública):** importes
  de compensación de ropa (T6), dietas/kilometraje (Soria), complemento IT
  confuso y celdas de tabla erróneas de Tenerife, divisor de horas extra
  desactualizado (Soria), plus fidelidad 2023 (Teruel), aplicación de nocturnidad
  de temporada y capital del seguro (Toledo), cómputo de jornada anual (Zamora),
  complemento IT desde qué día (Zamora).
- **Verificar contra imagen oficial del boletín (transcripción):** matriz de
  permisos y escala de antigüedad de Vizcaya; celdas 0,00 €, «#¡VALOR!» y
  discrepancia de manutención de Tenerife.
- **⚠️ ACTUALIZAR CONVENIO:** **ninguno.** La modificación 2026 del ALEH **aún no
  está en el BOE**; los convenios provinciales en ultraactividad no tienen tablas
  2026 nuevas publicadas. Todo `pendiente` sigue justificado.
