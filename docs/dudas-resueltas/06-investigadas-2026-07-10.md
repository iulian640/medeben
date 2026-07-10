# Dudas resueltas por investigación (2026-07-10)

> Continúa `00-transversales.md`. Misma regla: cada respuesta con su fuente
> oficial (artículo + norma del BOE, sentencia del TS/TJUE). Lo que dependa de la
> práctica de la empresa concreta se marca **[REQUIERE UGT/EMPRESA]**.
>
> Estas cierran varias de las preguntas abiertas de `docs/preguntas-ugt.md` que
> SÍ tienen respuesta en la ley y la jurisprudencia (no hacía falta la llamada).

---

## 1. Jerarquía de convenios: ¿quién fija el salario, el estatal o el provincial? (preguntas-ugt #1)

**Respuesta.** El **salario lo fija el convenio provincial/autonómico** aplicable,
no el estatal. El convenio estatal de hostelería (ALEH) es un **marco**: fija la
estructura de clasificación profesional y las materias reservadas, pero **no las
tablas salariales**, que se negocian abajo. Por eso la app selecciona el convenio
provincial (o autonómico donde exista) para el importe, y usa el ALEH solo para lo
que este reserva. **Es el diseño correcto.**

**Fundamento.** Art. 84 ET (RDLeg 2/2015), reformado por el RDL 32/2021 y por la
reforma posterior que da **prioridad aplicativa a los convenios autonómicos y
provinciales** sobre el estatal *cuando su regulación es más favorable para la
persona trabajadora*. El propio art. 84.4 ET **reserva al ámbito estatal** (no lo
puede tocar el provincial) una lista CERRADA de materias: periodo de prueba,
modalidades de contratación, **clasificación profesional**, **jornada máxima
anual**, **régimen disciplinario**, normas mínimas de PRL y movilidad geográfica.
El salario **no** está en esa lista → lo fija el convenio de ámbito inferior.

**Matiz para la app.** Cuando un trabajador está bajo restauración colectiva, el
salario viene del anexo provincial del convenio estatal del sector; en hostelería
general/hospedaje, del convenio provincial/autonómico. La app ya lo hace así
(`paraTrabajador(provincia, subsector)`).

Fuentes: [Art. 84 ET (Iberley)](https://www.iberley.es/legislacion/articulo-84-estatuto-trabajadores) ·
[Uría Menéndez — prevalencia condicionada de convenios autonómicos/provinciales](https://www.uria.com/es/publicaciones/8905-cambios-relevantes-en-materia-de-concurrencia-de-convenios-prevalencia-condicio)

---

## 2. Descanso entre jornadas: ¿es legal que Cataluña baje a 10 h? (preguntas-ugt #7b)

**Respuesta.** **Sí es legal en hostelería, pero con compensación.** La regla
general del ET son **12 h** entre el fin de una jornada y el inicio de la
siguiente (más protectora que las **11 h** de la Directiva europea, que es de
mínimos). Pero el sector de hostelería tiene **jornada especial**: el RD
1561/1995 permite que, **cuando el cambio de turno impide respetar las 12 h**, el
descanso se reduzca ese día hasta un **mínimo de 7 h**, y **la diferencia hasta
las 12 h se compense con descanso alternativo equivalente** en los días
inmediatamente siguientes. Por tanto bajar a 10 h es lícito **siempre que esas 2 h
se devuelvan** como descanso compensatorio.

**Regla para la app (suelo absoluto).** La app debe vigilar y avisar:
- Descanso entre jornadas **< 12 h** → hay **derecho a descanso compensatorio** por
  la diferencia (no es "gratis" para la empresa).
- Nunca por debajo de **7 h** en hostelería (suelo del RD 1561/1995).
- Suelos europeos que la app trata como absolutos: **11 h** de descanso diario,
  **48 h** de media semanal (con la referencia de cómputo), **4 semanas** de
  vacaciones (Directiva 2003/88).

**Fundamento.** Art. 34.3 ET (12 h; habilita al Gobierno a fijar jornadas
especiales) + RD 1561/1995 sobre jornadas especiales (reducción a 7 h con
compensación en hostelería y cambios de turno) + art. 3 Directiva 2003/88/CE
(11 h de mínimo europeo).

Fuentes: [Art. 34 ET (Iberley)](https://www.iberley.es/legislacion/articulo-34-estatuto-trabajadores) ·
[RD 1561/1995 (BOE-A-1995-21346)](https://www.boe.es/buscar/doc.php?id=BOE-A-1995-21346) ·
[Directiva 2003/88/CE (BOE-DOUE-L-2003-81852)](https://www.boe.es/buscar/doc.php?id=DOUE-L-2003-81852)

---

## 3. Calor: ¿cuándo se puede parar el trabajo? ¿hay umbral de grados? (preguntas-ugt #8)

**Respuesta.** **No hay umbral fijo de grados.** El disparador es la **alerta de
la AEMET (o del organismo autonómico) de nivel NARANJA o ROJO** por fenómenos
meteorológicos adversos. Cuando se activa esa alerta y las medidas preventivas ya
adoptadas **no son suficientes**, la empresa está **obligada a adaptar las
condiciones de trabajo**: reducir o modificar el horario, e incluso **prohibir
determinadas tareas** en las horas de más riesgo. Aplica al **trabajo al aire
libre** y a los centros que **no se pueden cerrar**.

**Para hostelería.** Terrazas, reparto, trabajo exterior → cubierto por esta regla
de alertas. **Cocina cerrada** → no va por la alerta AEMET sino por el régimen
general de lugares de trabajo (RD 486/1997: confort térmico, evaluación de riesgos
del puesto). Por eso la app avisa por **alerta AEMET naranja/roja**, no por "38 °C"
(la nota antigua de "si hace 38° no vas" era incorrecta y ya se corrigió).

**Fundamento.** RD-ley 4/2023, de 11 de mayo (BOE-A-2023-11187), que modifica el
RD 486/1997 añadiendo la obligación de adaptar la jornada ante alertas
meteorológicas adversas; sistema de protección **por alerta real**, no por umbral
fijo.

Fuentes: [RD-ley 4/2023 (BOE-A-2023-11187)](https://www.boe.es/buscar/act.php?id=BOE-A-2023-11187) ·
[UGT — regulación altas temperaturas RD-ley 4/2023](https://ugt-sp.es/regulacion-altas-temperaturasadaptacion-rd-ley-4-2023/)

---

## 4. Tope de horas extra: ¿lo modifica algún convenio de hostelería? (preguntas-ugt #3)

**Respuesta.** **No.** El tope de **80 h extraordinarias al año** es legal (art.
35.2 ET). El convenio puede **bajarlo**, nunca subirlo. Barrido de los **55
convenios del corpus**: todos mantienen 80 h (unos lo citan expresamente, otros
remiten al ET). Ninguno fija un tope distinto. El motor ya aplica 80 h y, cuando
el convenio lo cita en su articulado, muestra la cita del convenio en vez de la
genérica del ET (fix #182).

**Fundamento.** Art. 35.2 ET + verificación directa del corpus
(`horasExtraordinarias.tope*` en los 55 JSON).

Fuente: [Art. 35 ET (Iberley)](https://www.iberley.es/legislacion/articulo-35-estatuto-trabajadores)

---

## 5. Valor de la hora ordinaria: ¿cómo se calcula? ¿entran los pluses? (preguntas-ugt #3)

**Respuesta.** El ET no da una fórmula cerrada, pero el criterio consolidado (y el
que usan los propios convenios que sí publican fórmula) es:

> **valor hora ordinaria = (salario base × nº de pagas + pluses anuales computables) / jornada anual**

Los pluses **que retribuyen el trabajo ordinario** (plus de convenio, antigüedad,
etc.) **entran** en el numerador; la hora extra no puede pagarse por debajo de esa
hora ordinaria (art. 35.1 ET, suelo). Es exactamente lo que hace
`CalculoConvenioService.valorHoraOrdinaria`. Los convenios del corpus que publican
su fórmula (Cataluña, León, Córdoba, Cuenca…) confirman este esquema, con el
divisor = jornada anual del propio convenio.

**[REQUIERE UGT/EMPRESA]** qué pluses concretos son "computables" en un trabajador
dado puede variar; la app usa los publicados en el convenio y avisa de que el
salario real puede llevar complementos personales encima.

**Fundamento.** Art. 35.1 ET + fórmulas de valor hora transcritas en los propios
convenios (campo `formulaValorHora`/`formulaVHO` del corpus).

---

## Pendientes que SÍ necesitan a UGT o a la empresa (no las cierra la ley)

- **#5 "inicial" vs "garantizado"** (Madrid hostelería): cuál es el que se cobra y
  cuál se usa de base para extras/nocturnidad depende de la práctica del sector y
  de la redacción del convenio. Requiere confirmación.
- **#5b tazas de cafetería y % de servicio**: por qué 2 tazas > 3 tazas en algunas
  categorías (¿el % de servicio lo compensa?). Interpretación del convenio.
- **#7 prueba que pide Inspección** (fotos de cuadrantes, registros propios):
  práctica de la ITSS, no norma cerrada.
- **#12 Valladolid NS II vs NS III** y "administrativo" a secas: erratas/ambigüedad
  del propio BOP; lectura pro-trabajador a confirmar.
- **#9 colaboración de UGT**: decisión de la organización.
