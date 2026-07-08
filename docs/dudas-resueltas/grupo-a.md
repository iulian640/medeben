# Dudas resueltas — Grupo A (14 convenios)

> Convenios: A Coruña, Álava, Albacete, Alicante, Almería, Asturias, Ávila, Badajoz,
> Baleares, Burgos, Cáceres, Cádiz, Cantabria, Castellón.
>
> Regla suprema del proyecto: **nunca inventar**. Cada respuesta lleva su fuente oficial
> (artículo del convenio, ET, ALEH VI, STS/TJUE o boletín). Cuando algo depende de
> negociación de empresa o de un dato no publicado, se dice explícitamente.
>
> Fecha de resolución: **julio 2026**.

## Cómo leer este documento

- Las dudas **recurrentes** (retribución de vacaciones, permisos del RD-ley 5/2023,
  valor de la hora extra, ultraactividad, régimen disciplinario y periodo de prueba
  remitidos al ALEH) NO se re-investigan aquí convenio a convenio: se resuelven una sola
  vez en **`00-transversales.md`** y desde cada ficha se remite allí. Debajo va un
  resumen de una línea de cada una para no tener que saltar de fichero.
- Las dudas **específicas** de un convenio (un artículo concreto, una errata del anexo,
  un importe) se responden aquí con su cita.
- Las dudas de **actualidad/currency** ("¿se publicó ya la tabla 20XX?") se comprobaron
  contra el boletín oficial de cada provincia. Si hubiera tabla nueva se marcaría como
  **⚠️ ACTUALIZAR CONVENIO**. **Resultado del grupo A: ningún convenio requiere
  actualización** — todos los JSON están al día respecto a su última publicación oficial
  (ver sección final).

---

## Fuentes transversales (resumen; detalle en `00-transversales.md`)

| Tema recurrente | Respuesta corta | Fuente oficial |
|---|---|---|
| **Retribución de vacaciones** (base de cálculo cuando el convenio no la fija) | Se paga la **retribución normal o media**, no solo el salario base: incluye los complementos vinculados de forma habitual al puesto (nocturnidad habitual, festivos habituales…). No entran conceptos extrasalariales (transporte, manutención). | Art. 7 Directiva 2003/88/CE; **TJUE C-155/10 (Williams)** y **C-539/12 (Lock)**; **STS 4ª 8-06-2016 (rec. 207/2015)** y doctrina posterior. Art. 38 ET. |
| **Permisos RD-ley 5/2023** (convenios firmados antes de junio-2023) | Prevalece la ley por más favorable: 5 días por accidente/enfermedad grave, hospitalización o intervención de familiar hasta 2º grado; 4 días/año por fuerza mayor familiar; 15 días matrimonio/pareja de hecho. El convenio anterior no rebaja esto. | **Art. 37.3 ET** redacción del **RD-ley 5/2023** (BOE 29-06-2023). |
| **Valor de la hora extra** (convenio que no lo fija) | No puede ser inferior al valor de la hora ordinaria. | **Art. 35.1 ET**. |
| **Ultraactividad** | Denunciado el convenio, se mantiene vigente hasta nuevo acuerdo cuando así lo pacta (todos los del grupo A lo pactan expresamente). | **Art. 86.3 ET** + cláusula de cada convenio. |
| **Régimen disciplinario remitido al ALEH** | Cuadro de faltas y sanciones del **Capítulo VIII del ALEH VI**. | **ALEH VI**, BOE núm. 59 de **10-03-2023**. |
| **Periodo de prueba remitido al ALEH** | Duraciones por grupo profesional del **Capítulo III del ALEH VI**. | **ALEH VI**, BOE núm. 59 de 10-03-2023. |
| **Representación / crédito horario no detallado** | Régimen general. | **Arts. 62-68 ET** y **LOLS** (LO 11/1985). |

---

## A Coruña — `acoruna-hosteleria.json`

Fuente articulado: BOP A Coruña nº 241, 22-12-2022 (anuncio 2022/8018). Convenio 2019-2024
en ultraactividad; tablas 2024 vigentes.

1. **Base de cálculo de la retribución de vacaciones (art. 13 no la detalla).**
   → Recurrente. Ver `00-transversales.md`: retribución normal/media (salario base +
   complementos habituales), doctrina TJUE Williams / STS 8-06-2016. **No es "salario
   garantido só"**: si el trabajador percibe nocturnidad o festivos de forma habitual,
   entran en la media.

2. **Escala de faltas/sanciones y duración del periodo de prueba (remitidos al ALEH,
   arts. 36 y 18).**
   → Recurrente. ALEH VI (BOE 10-03-2023): faltas y sanciones Cap. VIII; periodo de
   prueba Cap. III. **Nota**: el convenio cita "ALEH" genérico; el vigente en julio-2026
   es el VI (2023). Verificar que la remisión sigue apuntando al texto en vigor.

3. **Cuantía del plus de limpieza/mantenimiento de ropa de trabajo (art. 30 no la fija).**
   → **Depende de negociación**: el art. 30 reconoce el derecho pero no cuantifica importe
   ni en tabla anexa. No hay fuente oficial que fije el euro. **Duda real para UGT**:
   ¿se abona por importe pactado en empresa, o se compensa el coste real de limpieza?
   (referencia: en otros convenios del sector se fija en tabla; aquí no).

4. **Mapeo función→nivel numérico.**
   → No es duda jurídica, es de modelado de datos. La estructura oficial de A Coruña es
   sección × área funcional × grupo × función × categoría de establecimiento (art. tablas
   BOP 241/2022), **no** nivel numérico 1-6. La app debe respetar esa estructura; no forzar
   un mapeo a niveles que el convenio no usa.

---

## Álava — `alava-hosteleria.json`

Fuente: BOTHA nº 20, 16-02-2026 (convenio 2025-2028). Estado verificado.

1. **Vacaciones: el suelo incluye "alojamiento en su caso" pero las tablas no fijan
   importe de alojamiento separado. ¿Cómo se cuantifica?**
   → **Duda real para UGT / empresa.** El alojamiento es salario en especie (art. 26.1
   ET): se valora por su coste real para el trabajador que lo disfruta. El convenio no lo
   tabula, así que su valoración es caso a caso. No hay importe oficial que citar.

2. **IT: el art. 26 exige prestación "otorgada por la Seguridad Social" para complementar;
   ¿qué pasa con los 3 primeros días de enfermedad común (sin prestación)?**
   → **Interpretación con fuente.** En enfermedad común, la prestación de IT nace el 4º día
   (art. 173 LGSS; los días 1-3 no generan prestación pública). Como el art. 26 del
   convenio condiciona el complemento a que exista prestación de la SS, **literalmente no
   hay complemento días 1-3** salvo que el convenio los cubra expresamente (no lo hace).
   Confirmar con UGT si la práctica de empresa mejora esto, pero **la letra del art. 26 +
   art. 173 LGSS** sostiene la lectura.

3. **Bonus 31 de octubre a jornada parcial calculado sobre el % del "parte de alta
   inicial": ¿y si la jornada aumentó después?**
   → **Duda real para UGT.** El convenio ancla el cálculo al parte de alta inicial; no
   prevé el supuesto de aumento posterior de jornada. No hay fuente que lo resuelva:
   es laguna del convenio → interpretación de la comisión paritaria.

4. **Periodo de prueba: correspondencia grupo↔nivel por categoría.**
   → Recurrente/modelado. Duraciones del ALEH VI Cap. III por grupo profesional; el mapeo
   categoría→grupo es tarea del comprobador de categoría. Ver `00-transversales.md`.

---

## Albacete — `albacete-hosteleria.json`

Fuente: BOP Albacete nº 109, 19-09-2025 (convenio anual 2025), con correcciones de errores
en BOP nº 121 (17-10-2025) y nº 143 (10-12-2025).

1. **¿Se aprobó y publicó ya la revisión salarial definitiva del art. 60 (IPC diciembre
   2025, hasta +0,5%, efectos 01/07/2025)? ¿En qué BOP?** *(currency)*
   → **Comprobado; sin confirmación de publicación.** La cláusula (art. 60) es
   **condicional**: solo se activa si el IPC anual cerrado a diciembre de 2025 supera el
   2,75 % pactado, revisando la diferencia con tope +0,5 % retroactivo al 01/07/2025.
   No se ha localizado en el BOP de Albacete una resolución de 2026 que publique esa
   revisión definitiva. Las publicaciones existentes (BOP 109, y correcciones 121/143)
   son el convenio y erratas del Anexo I, **no** la revisión del art. 60.
   **Acción**: NO es "ACTUALIZAR" confirmado (no hay tabla nueva localizada); queda como
   **ítem abierto a vigilar** en el BOP de Albacete de 2026. Fuente de la cláusula:
   art. 60 del convenio (BOP 109, 19-09-2025).

2. **Vacaciones (art. 48): ¿incluye promedio de complementos variables o solo base + Plus
   Convenio + antigüedad?**
   → Recurrente. Ver `00-transversales.md`: retribución media con complementos habituales
   (TJUE Williams / STS 8-06-2016). El art. 48 no lo excluye, así que rige esa doctrina.

3. **Permiso art. 52.a (5 días por accidente/enfermedad grave): ¿días naturales o
   laborables?**
   → **Interpretación con fuente.** El convenio calca la redacción del art. 37.3.b ET
   (RD-ley 5/2023), que **no** especifica; la doctrina judicial mayoritaria y el criterio
   de la Dirección General de Trabajo entienden **días naturales** salvo que el convenio
   diga "laborables". Aquí no lo dice → **naturales por defecto**, pero es **duda fina para
   UGT** porque hay litigiosidad al respecto. Fuente: art. 37.3.b ET; art. 52.a convenio.

4. **¿Existe negociación de un nuevo convenio plurianual que sustituya al anual 2025, o
   continúa la prórroga tácita?** *(currency)*
   → **Comprobado.** No consta en el BOP de Albacete un convenio plurianual que sustituya
   al anual de 2025. Rige la **prórroga tácita/ultraactividad del art. 4** (BOP 109). Fuentes
   sindicales (UGT-CLM, julio-2025) confirman la firma del convenio **anual** con +2,75 %,
   no de uno plurianual. No hay tabla nueva que transcribir.

---

## Alicante — `alicante-hosteleria.json`

Fuente tablas: BOP Alicante nº 29, 12-02-2026 (tablas 2026). Articulado: BOP Alicante
nº 200, 18-10-2023 (convenio 2023-2026). Estado verificado.

1. **Vacaciones: ¿se abona solo salario base o también promedio de festivos? (art. 27 no
   fija promedio de festivos; el art. 34 excluye nocturnidad en vacaciones).**
   → **Mixto, con fuente.** El propio convenio **excluye la nocturnidad** durante
   vacaciones (art. 34) — eso sí es dato del convenio, respétalo. Para el resto (festivos
   habituales), ver `00-transversales.md`: doctrina de retribución media. Punto fino: la
   exclusión expresa de nocturnidad podría chocar con la doctrina TJUE si esa nocturnidad
   es habitual → **duda para UGT** sobre si el art. 34 resiste. Fuente: arts. 27 y 34 del
   convenio; TJUE Williams.

2. **Festivos dentro de vacaciones (art. 27 no lo regula; los 14 festivos se descuentan
   del cómputo de jornada, art. 21).**
   → **Interpretación con fuente.** Regla general (art. 38 ET + art. 1.6 CC): las
   vacaciones se disfrutan en días de descanso retribuido; un festivo que cae dentro del
   periodo de vacaciones **no se descuenta** de los días de vacaciones. El convenio no lo
   contradice. **Duda menor**: confirmar con UGT si en la práctica de empresa se respeta.

3. **Mapeo categoría→nivel retributivo 1-6 (Anexo III) — el art. 15 remite al ALEH VI.**
   → Modelado, no duda jurídica. El convenio no da la equivalencia; hay que transcribirla
   del ALEH VI (grupos I/II/III y áreas funcionales 1ª-6ª, Caps. 2-3). Ver
   `00-transversales.md`. Fuente: art. 15 convenio + ALEH VI.

4. **Seguro de accidentes: cuantía del capital (art. 47) no leída celda a celda.**
   → **Dato pendiente de transcripción**, no duda jurídica: está en el art. 47 del
   articulado (BOP 200/2023). Leer y volcar el importe; no inventar.

---

## Almería — `almeria-hosteleria.json`

Fuente: BOP Almería nº 109, 10-06-2025 (convenio 2025 modificado). Ultraactividad total.

1. **Jornada anual en horas: el art. 21 solo fija 40 h/semana, no jornada anual.**
   → **Interpretación con fuente.** Si el convenio solo fija jornada semanal (40 h) y no
   una anual, la jornada máxima efectiva se obtiene por el cómputo del art. 34 ET (40 h
   semanales de promedio en cómputo anual) descontando festivos y vacaciones. No hay una
   cifra anual "oficial" del convenio que citar → la app debe **derivarla**, no inventar un
   número del convenio. **Duda para UGT** si se quiere una cifra pactada. Fuente: art. 21
   convenio; art. 34 ET.

2. **Retribución de vacaciones (art. 24 no fija fórmula).**
   → Recurrente. Ver `00-transversales.md` (retribución media). El art. 24 no la excluye.

3. **Baja/IT durante vacaciones (no recogido).**
   → **Resuelto por ley**, no es duda abierta: rige el **art. 38.3 ET** (si la IT impide
   disfrutar las vacaciones, se disfrutan después, con el límite de 18 meses). Cítalo así.

4. **Régimen disciplinario y periodo de prueba (arts. 67 y 14 → ALEH).**
   → Recurrente. ALEH VI Caps. VIII y III. Ver `00-transversales.md`.

5. **El art. 69 remite a un "art. 72" inexistente (el convenio acaba en el 70).**
   → **Errata de referencia cruzada** heredada del ALEH. No afecta a derechos; es defecto
   de técnica normativa. **Anótalo para UGT** como corrección deseable, pero no cambia la
   aplicación: la remisión efectiva es al régimen disciplinario del ALEH VI. Fuente: art. 69
   convenio (errata evidente).

---

## Asturias — `asturias-hosteleria.json`

Fuente articulado: BOPA nº 55, 21-03-2023 (convenio 2023-2027). Tablas 2026: BOPA nº 44,
05-03-2026. Estado verificado.

1. **Antigüedad (Anexo VII) 2026: el acta 2026 solo actualizó tabla salarial y
   nocturnidad; no republicó antigüedad. ¿Se actualiza el Anexo VII con el % anual?**
   → **Duda real para UGT.** El acta de la comisión paritaria de 2026 (BOPA 44/2026) no
   tocó el Anexo VII; por tanto, **oficialmente** siguen vigentes los importes de antigüedad
   anteriores (2023) hasta que un acta los actualice. No procede aplicar un % que el acta
   no publicó → **no inventar**. Confirmar con UGT si hubo acuerdo de actualización no
   publicado. Fuente: BOPA 44/2026 (alcance del acta).

2. **Retribución de vacaciones (art. 24.1 no especifica base).**
   → Recurrente. Ver `00-transversales.md`.

3. **Pluses de manutención y ropa de trabajo: sin importe fijo en euros.**
   → **Correcto y con base**: son prestaciones en especie / compensación de coste real
   (art. 26 ET); el convenio no las tabula en euros. No es error del JSON. Sin fuente que
   fije importe.

4. **Tablas 2024/2025/2027 en actas propias no transcritas.** *(currency-ish)*
   → **No inventar**: a julio-2026 rige la tabla 2026 (BOPA 44/2026), ya transcrita. Las de
   2024/2025 son históricas; la de 2027 aún no aplica. No hay nada que "actualizar" ahora.

---

## Ávila — `avila-hosteleria.json`

Fuente: BOP Ávila nº 33, 17-02-2023 (convenio 2023-2025). En ultraactividad desde 2026.

1. **Fiestas trabajadas: el convenio regula compensación en descanso pero NO recargo
   económico. ¿Hay recargo?**
   → **Con fuente: no lo hay en el convenio.** El convenio opta por **compensación en
   descanso** (disfrute como vacaciones), no por recargo económico del festivo trabajado.
   Es una opción legítima (art. 47 ET permite compensar el trabajo en festivo con descanso).
   **No inventes un recargo económico**: no existe en este convenio. Fuente: art. sobre
   fiestas del convenio + art. 47 ET.

2. **Fraccionamiento de vacaciones (nº de periodos / mínimo de días seguidos) no
   regulado.**
   → **Resuelto por ley**: a falta de pacto, rige el art. 38 ET (30 días naturales; el
   disfrute y fraccionamiento se acuerdan por calendario, sin mínimo legal de días seguidos
   salvo pacto). No es duda abierta.

3. **Baja/IT durante vacaciones no regulado.**
   → Art. 38.3 ET (igual que Almería). Resuelto por ley.

4. **Permisos: convenio de feb-2023 anterior al RD-ley 5/2023; el permiso por accidente/
   enfermedad grave (2 días conv.) queda por debajo del ET (5 días) y falta el de fuerza
   mayor familiar (4 días).**
   → Recurrente. **Prevalece la ley** (art. 37.3 ET redacción RD-ley 5/2023, BOE
   29-06-2023): 5 días y 4 días respectivamente. Ver `00-transversales.md`. El JSON ya lo
   marca bien.

5. **Régimen disciplinario, periodo de prueba, excedencias, garantías sindicales →
   ALEH/ET.**
   → Recurrente. Ver `00-transversales.md`.

---

## Badajoz — `badajoz-hosteleria.json`

Fuente: DOE nº 95, 20-05-2026 (acta 24-03-2026: tablas definitivas 2025 + **provisionales
2026**). Articulado: DOE nº 17, 27-01-2025 (convenio 2024-2026).

1. **2026 es PROVISIONAL (incremento 2,35 % sobre 2025 definitivo; podría subir hasta el
   IPC 2026, tope 3,25 %). ¿Se publica tabla definitiva de 2026?** *(currency)*
   → **Comprobado. No requiere actualización todavía.** La última publicación oficial es
   precisamente la del JSON (DOE 95, 20-05-2026), que fija 2026 como **provisional**. La
   tabla **definitiva** de 2026 no puede existir aún: depende del **IPC estatal cerrado a
   diciembre de 2026** (art. de revisión), que no se conoce hasta enero-2027. Por tanto el
   JSON está al día; **hay que volver a comprobar el DOE a principios de 2027**. No es
   "ACTUALIZAR" ahora. Fuente: acta 24-03-2026 (DOE 95/2026).

2. **Retribución de vacaciones (art. 14): el plus transporte y el salario en especie NO se
   perciben en vacaciones.**
   → **Correcto y con matiz.** Que el **plus transporte** (extrasalarial) y el salario en
   especie no se abonen en vacaciones **es coherente** con la doctrina: solo entran en la
   paga de vacaciones los conceptos **salariales** vinculados al trabajo habitual, no los
   extrasalariales (transporte, dietas). Ver `00-transversales.md`. Fuente: art. 14
   convenio + TJUE Williams (los extrasalariales quedan fuera).

3. **Crédito horario: "hasta 250 trabajadores 28 h" sin desglosar tramo 101-250.**
   → **Con fuente: prevalece el ET si mejora.** El art. 68.e ET fija una escala legal
   (p. ej. 101-250 → 20 h/mes). El convenio da 28 h "hasta 250", que es **más favorable**,
   así que se aplican 28 h a todo el tramo ≤250. No hay laguna real. **Confirmar con UGT**
   solo la práctica. Fuente: art. 68.e ET + convenio.

4. **Régimen disciplinario y periodo de prueba → ALEH.**
   → Recurrente. Ver `00-transversales.md`.

---

## Baleares — `baleares-hosteleria.json`

Fuente: BOIB nº 104, 05-08-2025 (convenio 2025-2028). Estado verificado.

1. **Precio/recargo de la hora extraordinaria: el convenio no lo fija.**
   → Recurrente. **Art. 35.1 ET**: no inferior al valor de la hora ordinaria. Ver
   `00-transversales.md`.

2. **Régimen disciplinario (Cap. 8 ALEH VI) y periodo de prueba (Cap. 3 ALEH VI).**
   → Recurrente. ALEH VI (BOE 10-03-2023). Ver `00-transversales.md`.

3. **Dietas/kilometraje en desplazamiento en misión (distintos del plus de desplazamiento
   del art. 28): ¿existen?**
   → **Con fuente: no consta en el convenio.** El art. 28 regula el plus de desplazamiento;
   el convenio no establece dietas ni kilometraje adicionales. A falta de pacto, el
   desplazamiento en misión se rige por lo pactado en empresa o el art. 40 ET (movilidad).
   **No inventar dietas**: si no están, no están. **Duda para empresa.** Fuente: art. 28
   convenio (silencio sobre dietas).

4. **Trabajo de categoría/nivel superior (polivalencia): condiciones exactas de pago
   (art. 11 + ALEH VI).**
   → **Con fuente.** Regla general **art. 39.3-4 ET**: el trabajo de superior categoría se
   retribuye por el salario de la función superior mientras se desempeñe; si se supera el
   tiempo legal (más de 6 meses en 1 año u 8 en 2), cabe reclamar el ascenso. El art. 11
   del convenio + ALEH VI concretan; leer el art. 11 para el detalle de importe. Fuente:
   art. 39 ET; art. 11 convenio.

---

## Burgos — `burgos-hosteleria.json`

Fuente articulado: BOP Burgos nº 68, 08-04-2024 (convenio 2023-2026). Tablas 2026: BOP
Burgos nº 54, 19-03-2026. Estado verificado.

1. **Retribución de vacaciones (art. 12 no especifica módulo).**
   → Recurrente. Ver `00-transversales.md`.

2. **Complemento personal, valores 2026: el PDF de tablas 2026 (BOP 54) no reeditó la
   tabla del art. 40; se muestran los importes 2024.** *(currency-ish)*
   → **No inventar.** Si la publicación de tablas 2026 (BOP 54/2026) no republicó el
   complemento personal del art. 40, **oficialmente siguen los importes del convenio base
   2024** hasta que se actualicen. Correcto mantener 2024. **Duda para UGT**: ¿el
   complemento personal se revaloriza con el % anual aunque el acta no lo reimprima?
   Fuente: alcance del BOP 54/2026.

3. **Pluses de manutención/alojamiento: el art. 45 remite a la ordenanza laboral
   (derogada, supletoria) sin fijar euros.**
   → **Con fuente: laguna real.** La Ordenanza de Hostelería de 1974 está **derogada**
   (disp. derog. ET); solo opera como derecho supletorio donde no haya pacto, pero no fija
   importes vigentes. El convenio no cuantifica → **no hay importe oficial**. **Duda para
   UGT/empresa.** Fuente: art. 45 convenio; derogación de ordenanzas (disp. derog. única
   ET).

4. **Antigüedad: sin escala de tramos, consolidada/congelada como complemento personal
   (art. 40). ¿Se sigue devengando por el ALEH?**
   → **Con fuente.** Muchos convenios de hostelería **congelaron** la antigüedad como
   complemento personal no absorbible (siguiendo la supresión del complemento de antigüedad
   del sector). Si el art. 40 la consolida como complemento personal, **no se devengan
   nuevos tramos** salvo que el convenio o el ALEH lo prevean expresamente. El ALEH VI no
   reintroduce antigüedad. → **No se sigue devengando**; se mantiene lo consolidado.
   **Confirmar con UGT.** Fuente: art. 40 convenio; ALEH VI.

5. **Régimen disciplinario y representación → ALEH/ET.**
   → Recurrente. Ver `00-transversales.md`.

---

## Cáceres — `caceres-hosteleria.json`

Fuente tablas definitivas 2025: DOE nº 59, 26-03-2026. Articulado: DOE nº 33, 17-02-2022
(convenio 2021-2025). En ultraactividad.

1. **Retribución de vacaciones (art. 19.A no especifica base).**
   → Recurrente. Ver `00-transversales.md`.

2. **Antigüedad (art. 34): da porcentajes sin nombrar la magnitud base.**
   → **Interpretación con fuente.** Cuando el convenio fija la antigüedad en % sin nombrar
   la base, la doctrina interpreta que se calcula sobre el **salario base de convenio**
   (no sobre el total con pluses), salvo mención expresa. → Asumir **salario base** es lo
   correcto, pero **confirmar con UGT** por la ambigüedad. Fuente: art. 34 convenio
   (interpretación estándar).

3. **Complemento IT (art. 20): no fija tope de días del complemento del 100 %. ¿Cubre toda
   la duración legal de la IT?**
   → **Con fuente: sí, salvo tope expreso.** Si el art. 20 reconoce el complemento al 100 %
   sin fijar tope, cubre mientras dure la IT dentro de sus límites legales (art. 169 LGSS:
   365 días prorrogables a 545). Sin tope en el convenio, **no se puede recortar**
   inventando uno. **Duda para UGT** solo para confirmar práctica. Fuente: art. 20 convenio;
   art. 169 LGSS.

4. **Permisos: convenio de enero-2022 sin las mejoras del RD-ley 5/2023.**
   → Recurrente. Prevalece la ley (5 días hospitalización/accidente grave; 4 días fuerza
   mayor). Ver `00-transversales.md`.

5. **Horas extraordinarias no reguladas → ALEH/ET.**
   → Recurrente. **Art. 35.1 ET** (valor no inferior a la hora ordinaria). Ver
   `00-transversales.md`.

6. **Régimen disciplinario, periodo de prueba, fijo-discontinuo, representación →
   ALEH/ET.**
   → Recurrente. Fijo-discontinuo: **art. 16 ET** + ALEH. Ver `00-transversales.md`.

---

## Cádiz — `cadiz-hosteleria.json`

Fuente: BOP Cádiz nº 153, 12-08-2025 (convenio 2025-2028). Estado: condiciones no
salariales verificadas.

1. **Vacaciones (art. 24): ¿días naturales o laborables?**
   → **Con fuente: naturales por defecto.** El art. 38 ET fija las vacaciones en "días" que
   la doctrina computa como **naturales** (30 días naturales = período mínimo) salvo que el
   convenio diga "laborables". El art. 24 no dice laborables → **naturales**. Fuente: art. 38
   ET; art. 24 convenio.

2. **Régimen disciplinario, periodo de prueba, representación → ALEH/ET.**
   → Recurrente. Ver `00-transversales.md`.

---

## Cantabria — `cantabria-hosteleria.json`

Fuente articulado: BOC nº 204, 24-10-2022 (convenio 2022-2025). Tablas vigentes: BOC nº 66,
04-04-2025 (tablas 2025). En ultraactividad.

1. **Errata del pie de las tablas 2025: remite al "art. 32" (que regula gratificaciones
   extraordinarias) para el SMI de altas <6 meses. ¿A qué artículo se refiere?**
   → **Errata confirmada / duda para UGT.** En el texto articulado de 2022 el art. 32 son
   las gratificaciones extraordinarias, no el salario de ingreso; la remisión del acta de
   tablas 2025 es **incoherente** con el articulado. Probable **errata o renumeración** del
   acta. No se puede resolver con fuente oficial: **pregunta directa a UGT** sobre a qué
   artículo se refieren realmente. Fuente del conflicto: pie de tablas BOC 66/2025 vs. art.
   32 del BOC 204/2022.

2. **¿Se están negociando ya las tablas de 2026 / un convenio 2025-2028?** *(currency)*
   → **Comprobado. No requiere actualización.** No se ha localizado en el **BOC** ninguna
   publicación de tablas 2026 ni de un convenio 2025-2028. Las menciones en webs de terceros
   **no están respaldadas por publicación oficial** → no se incorporan (regla de no
   inventar). Rige por **ultraactividad** el articulado 2022-2025 con las tablas 2025 (BOC
   66/2025). **Volver a comprobar el BOC periódicamente.** Fuente: ausencia de publicación
   en boc.cantabria.es.

3. **Manutención (art. 34): importe 18,47 €/mes del articulado 2022; no aparece actualizado
   en el acta de tablas 2025. ¿Valor 2025?**
   → **No inventar.** Si el acta de tablas 2025 no reeditó la manutención, **oficialmente
   sigue el importe del articulado (18,47 €/mes)**. **Duda para UGT** sobre si debía
   revalorizarse. Fuente: art. 34 (BOC 204/2022); alcance del acta BOC 66/2025.

4. **Festivos dentro de vacaciones (art. 16 no lo aclara).**
   → **Con fuente.** Un festivo que cae en el período vacacional no descuenta días de
   vacaciones (art. 38 ET, criterio general). Igual que Alicante. **Duda menor.**

5. **Complemento IT por accidente laboral (art. 28): ¿el tope de 12 meses aplica también a
   este supuesto o es indefinido mientras dure la IT?**
   → **Interpretación / duda para UGT.** Hay que leer si el tope de 12 meses del art. 28 se
   predica de toda IT o solo de la enfermedad común. La práctica del sector suele **no
   topar** el complemento en IT por **accidente de trabajo** (contingencia profesional),
   pero si el art. 28 lo topa expresamente, prevalece la letra. Ambiguo → **confirmar con
   UGT** el alcance exacto. Fuente: art. 28 convenio.

---

## Castellón — `castellon-hosteleria.json`

Fuente tablas 2026: BOP Castellón nº 32, 14-03-2026 (acta 23-01-2026). Articulado completo:
BOP Castellón nº 112, 18-09-2025 (convenio 2025-2028).

1. **Las tablas 2026 llevan rótulos heredados de la plantilla 2025 ("salario anual 2025",
   "(3,5 %)", "sumando un 3,5 %"). ¿Confirmáis que los importes del BOP nº 32 son los de
   2026?** *(currency + errata)*
   → **Resuelto con fuente; solo errata de rótulo.** El **acuerdo SEGUNDO del acta de
   23-01-2026** (publicada en BOP 32/2026) confirma que son las tablas **del año 2026**. Los
   rótulos "2025 / 3,5 %" son **arrastre de plantilla**, no afectan a los importes vigentes.
   El JSON usa correctamente esos importes como 2026. **Confirmar con UGT** la errata de
   cabecera para que la subsanen, pero **no cambia el dato**. Fuente: acta 23-01-2026, BOP
   Castellón 32/2026, acuerdo SEGUNDO.

2. **El anexo de conceptos 2026 rotula ropa de trabajo como "ART.29" y póliza de seguros
   como "ART.25", mientras el articulado las regula en art. 28 y art. 24. ¿Errata?**
   → **Errata del anexo, confirmada por contraste.** El articulado (BOP 112/2025) regula
   ropa de trabajo en el **art. 28** y seguros en el **art. 24**; el anexo de conceptos los
   cita con numeración distinta → **errata de referencia del anexo**. No afecta a derechos.
   **Anotar para UGT.** Fuente: arts. 24 y 28 del convenio (BOP 112/2025) vs. anexo BOP
   32/2026.

3. **Retribución de vacaciones (art. 16 no fija base). ¿Promedio de qué período?**
   → Recurrente. Ver `00-transversales.md`: retribución media (doctrina TJUE/STS). El
   período de promedio habitual es el trimestre/año anterior; a falta de pacto lo fija la
   práctica/comisión paritaria. El art. 16 no lo excluye.

---

## Resumen currency — comprobación de boletines

| Convenio | Ítem de actualidad | Resultado | ¿ACTUALIZAR? |
|---|---|---|---|
| Albacete | Revisión definitiva art. 60 (+0,5 % IPC dic-2025) | Cláusula condicional; **sin resolución 2026 localizada** en BOP | No (vigilar BOP 2026) |
| Albacete | ¿Convenio plurianual nuevo? | No; sigue anual 2025 en ultraactividad | No |
| Badajoz | Tabla **definitiva** 2026 | Aún provisional; depende del IPC 2026 (no cerrado) | No (revisar DOE en 2027) |
| Cantabria | Tablas 2026 / convenio 2025-2028 | **Sin publicación en BOC**; rige ultraactividad 2025 | No (vigilar BOC) |
| Castellón | ¿Tablas del BOP 32 son de 2026? | **Sí**, confirmado por acta 23-01-2026 (solo errata de rótulo) | No |
| Asturias | Tablas 2024/2025/2027 no transcritas | Rige 2026 (ya transcrita); resto histórico/futuro | No |

**Ningún convenio del grupo A requiere transcribir una tabla nueva a fecha de julio 2026.**

---

## Para llevar a UGT (dudas que dependen de verdad de interpretación paritaria o de empresa)

Estas NO tienen respuesta cerrada en fuente oficial; hay que preguntar:

- **Álava**: valoración del alojamiento en especie en vacaciones; complemento IT días 1-3
  de enfermedad común; bonus 31-oct si la jornada parcial aumentó tras el alta inicial.
- **A Coruña**: importe del plus de limpieza/mantenimiento de ropa (art. 30, no tabulado).
- **Asturias**: si el Anexo VII (antigüedad) se actualiza con el % anual aunque el acta 2026
  no lo republicara.
- **Burgos**: revalorización del complemento personal (art. 40) y de los pluses de
  manutención/alojamiento (art. 45, ordenanza derogada); si la antigüedad congelada sigue
  o no devengándose.
- **Cantabria**: a qué artículo se refiere realmente el pie de tablas 2025 (errata "art.
  32"); valor 2025 de la manutención; alcance del tope de 12 meses del complemento IT por
  accidente laboral (art. 28).
- **Alicante**: si la exclusión de nocturnidad en vacaciones (art. 34) resiste frente a la
  doctrina TJUE cuando la nocturnidad es habitual.
- **Albacete**: si el permiso del art. 52.a (5 días) es natural o laborable (litigioso).
- **Baleares**: existencia de dietas/kilometraje en misión más allá del plus del art. 28.

## Erratas de los boletines a señalar (no cambian derechos)

- **Almería**: art. 69 remite a un "art. 72" inexistente.
- **Castellón**: rótulos "2025 / 3,5 %" en tablas que son de 2026; anexo cita "ART.29/ART.25"
  frente a arts. 28/24 del articulado.
- **Cantabria**: pie de tablas 2025 remite al "art. 32" (que regula otra cosa).
