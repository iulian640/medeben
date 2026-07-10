# MeDeben

> Read this in English → [README.md](README.md)

**Tu convenio y tus horas, en claro.**

App gratuita para trabajadores de la hostelería en España. Consulta el convenio
colectivo que te corresponde, entiende tu nómina y lleva tus horas al día, todo
explicado en lenguaje llano y con cada dato acompañado de su artículo y del
enlace al boletín oficial.

Dentro hay 55 convenios de hostelería (las 50 provincias más Ceuta y Melilla)
transcritos de los boletines oficiales. Si un dato no está publicado, la app lo
dice; nunca lo inventa.

> 🚧 **En construcción (v1).** Todavía la estoy desarrollando. Mira el
> [Estado](#estado) para ver qué funciona hoy y qué está en camino.

## Qué hace

- Te dice qué convenio te corresponde según tu provincia y el tipo de
  establecimiento donde trabajas.
- Eliges tu puesto en un desplegable ("cocinero/a", "camarero/a"...) y te
  explica tu nivel y el salario que fija tu convenio, con el artículo citado.
- Te dice cuánto te deben este mes: compara tu horario con lo que fichaste y
  calcula el mínimo que te corresponde por las horas de más.
- Calculadora de horas extra según tu convenio, con sus fuentes.
- Registro de horas sencillo: apuntas tu horario y la app te avisa para
  fichar con un toque. Un día sin fichar queda como hueco, a la vista: la app
  nunca rellena horas que no registraste. A los 14 días el diario se sella con
  fecha del servidor; eso es lo que convierte tu libreta en una prueba.
- Guarda tus cuadrantes con fecha, para que tengas tu propio historial ordenado
  (y quede constancia de los cambios de turno de última hora).
- Exporta informes PDF (del mes y del año) con tus registros, sus sellos de
  fecha y el detalle de cada cálculo, listos para llevar a un sindicato o a un
  abogado.

## Nuestro compromiso

- Nunca pagarás por usarla.
- Tus datos son tuyos: expórtalos o bórralos cuando quieras. No los vendemos.
- Lo que hoy es gratis no se convertirá en de pago.
- No hace falta creernos: el código es público (AGPL-3.0) y cualquiera puede
  comprobar qué hace la app con tus datos.

## Quién ha hecho esto, con franqueza

Soy Iulian, excocinero y ahora aprendiendo a programar. MeDeben existe porque
viví el problema: nunca supe del todo qué decía mi convenio, ni si mis horas
cuadraban a final de mes.

Este es el reparto honesto, porque toda la app va de ser claro con tus derechos
y sería raro esconder cómo está hecha.

La idea es mía, y también cada decisión de producto: para qué sirve la app, para
quién es, qué haremos y qué no, y la regla que lo gobierna todo (un dato erróneo
es peor que uno ausente). Los convenios los investigamos juntos, porque esa
parte necesita a alguien que haya currado en una barra y sepa qué significa un
"grupo de actividad" en una nómina. Yo reviso lo que entra, y lo pruebo con
gente que sigue en las cocinas.

El código, en su mayoría, no es mío. El backend en Java/Spring, el frontend en
Vue, el pipeline de transcripción, los tests, la CI: casi todo lo escribe
[Claude Code](https://claude.com/claude-code), un agente de programación con IA,
trabajando como mi pareja y bajo mi dirección. Teclea casi todo y toma muchas de
las decisiones de implementación. Yo pongo el rumbo, mantengo los estándares y
digo que no cuando algo está mal.

Así un proyecto de este tamaño ha tomado forma en semanas y no en meses. El
criterio es mío; las manos son sobre todo de la IA. Ese es el trato, y prefiero
que lo sepas.

## Los datos

Los convenios viven en [`convenios/`](convenios/) como ficheros de datos, cada
cifra con su artículo y su boletín de origen. Un validador automático comprueba
en cada build que las capas derivadas coinciden con la transcripción. ¿Ves un
error o falta tu provincia? Se corrige con un PR.

## Cómo está hecho

Lo de dentro importa tanto como lo de fuera:

- **Transcripción contra la imagen del PDF oficial.** El texto extraído de los
  boletines desalinea las columnas, así que cada tabla se lee renderizada a
  imagen, celda a celda, y después una segunda pasada independiente la
  re-verifica. Regla de oro: un dato erróneo es peor que un dato ausente. Lo
  que no está publicado se marca como pendiente; no se rellena.
- **Procedencia verificable.** Los números que usa la app viven en una capa
  normalizada donde cada hecho lleva un puntero a la celda exacta de la
  transcripción de la que sale. Un validador cruzado corre en cada build y lo
  rompe si un solo importe diverge.
- **Cada respuesta con su fuente.** Los cálculos citan el artículo del convenio
  y enlazan al PDF del boletín (o al BOE, para el Estatuto de los Trabajadores).
- **Calidad de código.** TDD en backend y frontend, revisión de código y de
  seguridad (humana y asistida por IA) en cada pieza importante, y una CI que
  ejecuta la suite completa en cada PR: el validador del corpus, tests contra
  PostgreSQL real y los recorridos de usuario de punta a punta (Playwright)
  contra el stack levantado de verdad.
- **Las rarezas se apuntan.** Al transcribir aparecen cosas curiosas (un plus de
  nocturnidad del 1%, un grupo que cobra más en 3ª categoría que en 2ª...):
  están recogidas con su fuente en [convenios/curiosidades.md](convenios/curiosidades.md).

## Estado

🚧 En desarrollo (v1). Funciona hoy: consulta del convenio, salario por puesto
con fuentes, "cuánto te deben este mes", registro de horas con diario sellado,
editor de cuadrantes, informes PDF del mes y del año, avisos de fichaje (app
Android) y cuentas con borrado real (RGPD) y sesiones que se pueden cerrar de
verdad. En camino: la publicación (Play Store) y el despliegue público. El
corpus completo de 55 convenios (~8.800 hechos salariales con procedencia) ya
está en su sitio.

Decisiones del proyecto: [docs/ADR.md](docs/ADR.md). Diario de avance:
[docs/HISTORIAL.md](docs/HISTORIAL.md). Para levantarlo en local:
[docs/dev-setup.es.md](docs/dev-setup.es.md).

## Stack

- **Backend:** Java / Spring Boot
- **Frontend:** Vue 3 (web PWA + Android vía Capacitor)
- **Base de datos:** PostgreSQL

## Licencia

[AGPL-3.0](LICENSE): libre para siempre, para cualquiera.
