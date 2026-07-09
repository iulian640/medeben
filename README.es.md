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
- Calculadora de horas extra según tu convenio, con sus fuentes.
- Registro de horas sencillo: apuntas tu horario y la app te avisa para
  ficharlo con un toque. Si un día no contestas, asume tu horario habitual
  (marcado como automático, corregible después).
- Guarda tus cuadrantes con fecha, para que tengas tu propio historial ordenado.
- Exporta un informe PDF con tus registros y el detalle de los cálculos
  *(planeado)*.

## Nuestro compromiso

- Nunca pagarás por usarla.
- Tus datos son tuyos: expórtalos o bórralos cuando quieras. No los vendemos.
- Lo que hoy es gratis no se convertirá en de pago.
- No hace falta creernos: el código es público (AGPL-3.0) y cualquiera puede
  comprobar qué hace la app con tus datos.

## Cómo está hecha

Soy Iulian, excocinero y ahora desarrollador en formación. Empecé MeDeben
porque viví el problema: no saber qué decía de verdad mi convenio, ni si mis
horas cuadraban.

Yo pongo la dirección y las decisiones de producto, y reviso lo que entra. Una
regla lo gobierna todo: un dato erróneo es peor que uno ausente. La construcción
la hago con [Claude Code](https://claude.com/claude-code) (un agente de
programación con IA) como pareja: el backend en Java/Spring, el frontend en Vue,
el pipeline de transcripción y validación, los tests y la CI se escriben con él,
bajo mi dirección y revisión. El criterio de dominio y los estándares son míos;
la IA teclea gran parte.

Esa forma de trabajar es también por qué un proyecto de este tamaño ha tomado
forma en semanas, no en meses.

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
  ejecuta la suite completa en cada PR, incluido el validador del corpus y
  tests contra PostgreSQL real.
- **Las rarezas se apuntan.** Al transcribir aparecen cosas curiosas (un plus de
  nocturnidad del 1%, un grupo que cobra más en 3ª categoría que en 2ª...):
  están recogidas con su fuente en [convenios/curiosidades.md](convenios/curiosidades.md).

## Estado

🚧 En desarrollo (v1). Funciona hoy: consulta del convenio, salario por puesto
con fuentes, el motor de cálculo de hora/horas extra, registro de horas y
cuadrantes, y cuentas de usuario (JWT). En camino: exportación a PDF y avisos de
fichaje. El corpus completo de 55 convenios (~8.800 hechos salariales con
procedencia) ya está en su sitio.

Decisiones del proyecto: [docs/ADR.md](docs/ADR.md). Diario de avance:
[docs/HISTORIAL.md](docs/HISTORIAL.md). Para levantarlo en local:
[docs/dev-setup.es.md](docs/dev-setup.es.md).

## Stack

- **Backend:** Java / Spring Boot
- **Frontend:** Vue 3 (web PWA + Android vía Capacitor)
- **Base de datos:** PostgreSQL

## Licencia

[AGPL-3.0](LICENSE): libre para siempre, para cualquiera.
