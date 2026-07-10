# DESIGN.md — MeDeben

Sistema visual "**Nómina clara**": la claridad de un documento oficial, pero a
favor del trabajador. Papel blanco, tinta casi negra, y el verde reservado para
el dinero y la acción. Registro *product* (el diseño sirve a la tarea); móvil
primero (PWA + Capacitor); tema claro/oscuro siguiendo al sistema.

La fuente de verdad de los tokens es `frontend/src/style.css`. Este documento
explica cómo usarlos.

## Color

| Token | Papel |
|---|---|
| `--papel` / `--papel-2` | Fondo y segunda superficie (nav, filas, paneles) |
| `--tinta` / `--tinta-suave` | Texto principal y secundario (AA garantizado) |
| `--linea` / `--linea-fuerte` | Separadores y bordes de controles |
| `--verde` / `--verde-vivo` / `--sobre-verde` / `--verde-suave` | SOLO dinero, acción primaria y selección |
| `--alerta` / `--alerta-suave` | Avisos y errores |

Reglas: el verde nunca es decoración; la **cifra del dinero va en tinta** (el
verde la subraya, no la pinta); texto secundario con `--tinta-suave`, **nunca
`opacity`**; el tema oscuro sale de los mismos tokens (no hay estilos `dark`
por componente).

## Tipografía

Public Sans variable (100–900, subset local de 25 KB con `tnum`). Escala fija
en rem: `--tipo-xs/sm/base/lg/xl/titulo/importe` (12/14/16/19/23/28/56).
Pesos por rol: 400 texto, 600 etiqueta/botón (`--peso-etiqueta`), 700 título,
800 importe. Cifras alineables → clase `.num` (numerales tabulares).
`text-wrap: balance` ya viene de base en h1–h3. **Prohibido**: mayúsculas
espaciadas como etiqueta de sección (usar `.titulo-seccion`), tamaños fuera de
la escala.

## Espaciado y forma

Escala 4pt: `--esp-2xs…--esp-2xl` (4/8/12/16/24/32/48). Ritmo: apretado dentro
de un grupo (`--esp-xs`), generoso entre secciones (`--esp-lg/xl`); que no todo
respire igual. Radios: `--radio-control/tarjeta/pastilla`. Sombras
`--sombra-1/2` (elevación real, no decoración). Z: `--z-nav/velo/flotante`.
Layout de vista: columna `max-width: 30rem; margin-inline: auto` con
`padding: var(--esp-lg) var(--esp-md) var(--esp-2xl)`.

## Componentes (vocabulario único)

Clases globales de `style.css` — **no reinventar por vista**:

- `.boton` (primario verde, máx. uno por pantalla), `.boton-secundario`,
  `.boton-fantasma`, modificador `.boton--ancho`. Estados hover/active/disabled
  ya resueltos; altura mínima 44 px.
- `.campo` (label + input/select), `.campo--error`, `.campo-ayuda`,
  `.campo-error`.
- `.tarjeta` (nunca anidadas), `.titulo-seccion`, `.aviso-bloque` (fondo teñido
  y borde completo — **prohibida la franja lateral** `border-left`),
  `.texto-suave`, `.texto-sm`, `.texto-xs`, `.num`.
- `PanelPlegable` (despliegue inline animado, alternativa al modal),
  `ImporteDinero` (la cifra con cuenta y subrayado), `CitasFuente` (fuentes).

## Movimiento

Todo por `lib/animacion.ts` (anime.js) o transiciones CSS con los tokens
`--dur-*` y `--curva-*`. El movimiento **comunica estado**, no decora:

- Momento firma: la cifra del resumen cuenta de 0 al importe (800 ms outExpo)
  y el trazo verde la subraya al llegar (450 ms outQuint).
- Feedback: `pulsoExito` al confirmar; transición de pantallas `vista` (200 ms).
- Listas recién cargadas: `revelaEscalonado` (tope 400 ms en total).
- `prefers-reduced-motion`: estado final al instante, sin excepciones (CSS por
  media query; anime.js por el guard de `animacion.ts`).
- Prohibido: rebotes/elásticos, animar `width/height/top/left`, coreografías
  de carga de página, motion decorativo.

## Copy

El texto es parte del diseño y **ya está trabajado** (lenguaje llano, encuadre
de protección): al rediseñar una vista se conserva el copy tal cual salvo
acuerdo explícito.
