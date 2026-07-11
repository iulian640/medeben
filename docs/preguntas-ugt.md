# Preguntas para UGT (dudas abiertas del proyecto)

Lista para llevar a la llamada con UGT. Cada duda salió de transcribir convenios
reales. Objetivo: confirmar cómo interpretar/aplicar bien los datos en la app.

## 1. Jerarquía y concurrencia de convenios (la gorda)
- ¿Cómo se decide qué convenio aplica a un trabajador cuando hay uno **estatal**
  (marco) y uno **provincial/autonómico** (tablas)? ¿El provincial siempre pone
  el salario? ¿En qué materias manda cada uno (art. 84 ET)?
- Si el provincial y el estatal difieren en algo (p. ej. nocturnidad), ¿se aplica
  el más favorable al trabajador, o gana un nivel por regla fija?

## 2. Subsectores dentro de "hostelería"
- Confirmar que en cada provincia hay convenios separados para **hospedaje
  (hoteles)** y **hostelería (bares/restaurantes)**, y que **restauración
  colectiva** (comedores, hospitales) va por el estatal. ¿Es así en todas las
  provincias o varía?
- ¿Cómo sabe un trabajador con seguridad cuál le aplica? (código REGCON, objeto
  de la empresa, lo que ponga su contrato...)

## 3. Horas extra
- En el convenio de hospedaje de Madrid no aparece precio de hora extra para
  plantilla (solo 22,65 € para "servicios extras"). ¿Se aplica entonces el ET
  (hora extra = mínimo valor de la hora ordinaria)? ¿O hay otra regla del sector?
- ¿El tope de 80 h/año del ET, lo modifica algún convenio de hostelería?
- ¿Cómo se calcula "el valor de la hora ordinaria" oficialmente? ¿(salario base
  × 14 + pluses) / jornada anual? ¿Entran los pluses en el divisor?

## 4. Nocturnidad
- En Hostelería de Madrid la nocturnidad de 22:00-00:00 es del **1%** y de
  00:00-08:00 del 25%. ¿Es correcto que el tramo 22-00 sea tan bajo? ¿Se aplica
  de verdad así en nómina?

## 5. "Salario garantizado" vs "salario inicial"
- En el convenio de hostelería las tablas tienen columnas **inicial** y
  **garantizado**. ¿Cuál es el salario real que cobra el trabajador y cuál se usa
  para calcular horas extra y nocturnidad?

## 5b. Cafeterías, "tazas" y porcentaje de servicio
- Confirmar el sistema de "tazas" (1/2/3) para clasificar cafeterías.
- En las tablas de cafeterías con servicio de sala, los importes son "sueldo
  inicial" y en varias categorías el de 2 tazas es MAYOR que el de 3 tazas.
  ¿Es porque encima va el porcentaje de servicio y las de 3 tazas generan más?
  ¿Cómo se calcula el salario TOTAL real (inicial + % servicio)?

## 6. Ultraactividad
- El convenio de Hostelería de Madrid 2023-2025 expiró el 31-12-2025. ¿Las
  tablas de 2025 siguen vigentes en 2026 por ultraactividad? ¿Hasta cuándo?

## 7. Denuncias e Inspección
- Confirmar la diferencia entre denuncia formal (confidencial) y el Buzón de
  lucha contra el fraude (anónimo). ¿Qué prueba pide Inspección? ¿Sirven fotos de
  cuadrantes y registros hechos por el propio trabajador?

## 7b. Descanso entre jornadas y suelo europeo
- El convenio de Cataluña permite reducir el descanso entre jornadas a 10 h. La UE
  (Directiva 2003/88) exige 11 h y el ET 12 h. ¿Es legal bajar a 10 h? ¿Obliga a
  dar **descanso compensatorio** por esa hora? ¿Cómo se reclama?
- ¿Qué mínimos europeos conviene que la app vigile como suelo absoluto (48 h/sem,
  11 h descanso, 4 semanas vacaciones)?

## 8. Calor / condiciones climáticas
- ¿Qué dice exactamente el RD-ley 4/2023 sobre parar el trabajo por calor? ¿Hay
  umbral de grados o va por alerta AEMET? ¿Aplica a cocinas/terrazas?

## 9. ¿Colaboración?
- ¿A UGT le interesaría estar en una app gratuita del lado del trabajador de
  hostelería (contactos, difusión, revisión de contenido)? (posible patrocinio /
  aval, ver ADR D1).

## 12. Clasificaciones contradictorias o ambiguas detectadas al mapear puestos (2026-07-08)

Pendientes de re-verificar contra el PDF oficial; si el boletín de verdad dice esto,
¿cómo se interpreta?

- ~~Sevilla~~ y ~~Cádiz~~: RESUELTAS 2026-07-08 re-verificando la imagen del PDF —
  eran errores nuestros de transcripción, ya corregidos (camarero de Sevilla = Nivel 3;
  el "Cocinero/a (ayte.)" del grupo 4 de Cádiz no existe en el BOP).
- **Valladolid (SIGUE ABIERTA):** errata del PROPIO BOP verificada: la tabla 2026 lista
  al jefe de partida en NS II y NS III a la vez; la tabla 2025 y la estructura del resto
  de áreas apuntan a NS III (NS II = segundos jefes). ¿Se aplica NS III, o el trabajador
  puede exigir NS II (lectura más favorable) mientras el BOP no publique corrección?
- **Ambigüedad recurrente en media España:** "administrativo/a" no existe como
  categoría llana (solo oficial 1ª/2ª, auxiliar...). ¿Qué categoría se presume si el
  contrato dice solo "administrativo"?

## 13. Restauración colectiva: dos puestos en NULL por ambigüedad REAL del BOE (verificado 2026-07-11)

Verificadas contra el PDF oficial (**BOE-A-2025-12598**, convenio estatal de
restauración colectiva, BOE núm. 148 de 20-jun-2025, anexos de Cataluña págs.
82197-82199). En ambos casos el texto oficial NO desambigua: no son fallos de
transcripción, son ambigüedad de fondo del propio convenio. Se quedan en `null`
(dato ausente > dato erróneo) hasta que UGT confirme.

- **Girona — "Jefe/a de sala":** el BOE lista *literalmente* el mismo puesto en
  **dos niveles con salarios distintos**: NIV.1 (1.739,97 €/mes, junto a jefe/a de
  administración/comercial/cocina) y NIV.3 (1.553,64 €/mes, junto a jefe/a de
  partida/sector). El articulado (art. 13) dice que las ocupaciones son "meramente
  enunciativas" y remite al ALEH, que no mapea a la numeración NIV. **Pregunta:**
  *¿bajo qué criterio se aplica NIV.1 vs NIV.3 al mismo "Jefe/a de sala"? (¿tamaño
  del centro, catering vs comedor, dependencia jerárquica?)* — igual pasa con
  "Segundo/a Jefe/a de sala" (NIV.2 y NIV.3).
- **Lleida — "personal de limpieza":** son **dos oficios distintos** a dos
  precios: "Aux. de Servicio y Limpieza" (NIV.4, 1.282,94 €/mes = sirve la línea
  Y limpia) vs "Auxiliar de limpieza / Fregador-limpiador" (NIV.5, 1.231,63 €/mes
  = solo limpia). El convenio no define funciones. **Pregunta:** *un trabajador de
  comedor de colectividad cuya función es limpiar, ¿se encuadra en "Auxiliar de
  limpieza" (NIV.5) o en "Auxiliar de Servicio y Limpieza" (NIV.4)? ¿Qué función
  marca la frontera?* — Si hubiera que elegir hoy, el candidato conservador es
  NIV.5 (importe más bajo, nunca sobreestima la deuda), pero no se aplica sin
  confirmación.
