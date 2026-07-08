# TeDeben

**Tu convenio y tus horas, en claro.**

App gratuita para trabajadores de la hostelería en España. Consulta el convenio
colectivo que te corresponde, entiende tu nómina y lleva tus horas al día, todo
explicado en lenguaje llano y con cada dato acompañado de su artículo y del
enlace al boletín oficial.

Dentro hay 55 convenios de hostelería (las 50 provincias más Ceuta y Melilla)
transcritos de los boletines oficiales y verificados celda a celda. Si un dato
no está publicado, la app lo dice; nunca lo inventa.

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
- Exporta un informe PDF con tus registros y el detalle de los cálculos.

## Nuestro compromiso

La hizo un excocinero que conoce el sector desde dentro.

- Nunca pagarás por usarla.
- Tus datos son tuyos: expórtalos o bórralos cuando quieras. No los vendemos.
- Lo que hoy es gratis no se convertirá en de pago.
- No hace falta creernos: el código es público (AGPL-3.0) y cualquiera puede
  comprobar qué hace la app con tus datos.

## Los datos

Los convenios viven en [`convenios/`](convenios/) como ficheros de datos, cada
cifra con su artículo y su boletín de origen. Un validador automático comprueba
en cada build que las capas derivadas coinciden con la transcripción. ¿Ves un
error o falta tu provincia? Se corrige con un PR.

## Estado

🚧 En desarrollo (v1). Decisiones del proyecto: [docs/ADR.md](docs/ADR.md).
Diario de avance: [docs/HISTORIAL.md](docs/HISTORIAL.md).
Para levantarlo en local: [docs/dev-setup.md](docs/dev-setup.md).

## Stack

- **Backend:** Java / Spring Boot
- **Frontend:** Vue 3 (web PWA + Android vía Capacitor)
- **Base de datos:** PostgreSQL

## Licencia

[AGPL-3.0](LICENSE): libre para siempre, para cualquiera.
