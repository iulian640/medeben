# Revisiones pendientes del corpus

Datos que NO están en las fuentes ya transcritas y que por la regla de oro
(**nunca inventar**) quedan marcados `pendiente` en su JSON. Cada entrada dice
qué falta, dónde buscarlo y por qué no se rellenó. Al cerrarse una, se actualiza
el JSON y se borra la línea.

## codigoRegcon

(Sección cerrada. Nota de la pasada 2026-07-08: de los 4 ficheros que no tenían
`codigoRegcon`, en 3 el código SÍ estaba ya transcrito dentro de
`fuente.documento` y solo se aplanó a la clave canónica (sin inventar nada):
cadiz `11000065011981`, leon `24002505011979`, zamora `49001205011981`. El
cuarto, ceuta-hosteleria, se cerró después verificando `51100205012025` en la
consulta pública oficial del REGCON.)

## Jornada anual ilegible para el motor (dato ausente en la fuente, no re-estructurable)

Estos convenios NO fijan jornada anual en horas (verificado en su articulado);
el motor no puede calcular valor hora hasta que una revisión/consulta lo cierre:

- **almeria-hosteleria**: Art. 21 solo fija 40 h/semana. Duda para UGT anotada en el JSON.
- **ourense-hosteleria**: Art. 6 solo fija 40 h/semana y 9 h/día máximo.
- **zamora-hosteleria**: Art. 16 solo fija 40 h/semana. Duda para UGT anotada en el JSON.
- **ceuta-hosteleria**: Arts. 8-10, `jornadaAnualHoras: "no_previsto"`.
- **aleh-estatal** y **estatal-restauracion-colectiva**: sin bloque de jornada/pagas
  aplicable al cálculo genérico (acuerdo marco / estructura por anexos).

## Formato de vigencia de aleh-estatal

`vigencia.desde: "2023"` y `hasta: "pendiente"` no siguen el formato fecha
completa del resto del corpus, pero armonizarlo exigiría inventar día y mes de
entrada en vigor (la fuente transcrita solo acredita la publicación en BOE el
10-03-2023). El motor tolera el formato (test `alehEstatalTolerado`). Si una
revisión del ALEH VI verifica en imagen su cláusula de vigencia, actualizar y
armonizar entonces.
