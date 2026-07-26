# Ficha de Google Play — textos de la tienda

> Textos listos para pegar en Play Console (ficha principal, castellano de
> España). Voz: directa, de trabajador a trabajador, sin marketing hueco.
> Escritos con la skill `avoid-ai-writing`. **Sin promesas de resultados
> legales:** los cálculos son orientativos y así se dice en la propia ficha.
> No se menciona la historia personal del autor.
>
> Límites de Play (research §7): título ≤ 30 car., descripción corta ≤ 80,
> descripción larga ≤ 4000.
> Fuente: https://support.google.com/googleplay/android-developer/answer/9859152

---

## Título (elegido) — 25/30 car.

```
MeDeben: horas y convenio
```

## Descripción corta (elegida) — 78/80 car.

```
Ficha tus horas, calcula lo que te deben y consulta tu convenio de hostelería.
```

---

## Descripción larga (elegida) — ~2.050/4.000 car.

```
Si trabajas en hostelería, seguramente no tienes claro qué dice tu convenio ni si las horas te cuadran a final de mes. MeDeben es para eso.

Apuntas tu horario y fichas la entrada y la salida con un toque. La app compara lo que fichaste con tu horario y te dice el mínimo que te deben por las horas de más, según tu convenio y citando el artículo. Un día que no fichas se queda como un hueco, a la vista: la app nunca rellena horas que no registraste.

Qué hace:

- Te dice qué convenio te corresponde por tu provincia y el tipo de local donde trabajas.
- Eliges tu puesto (cocinero/a, camarero/a, ayudante...) y te explica tu nivel y el salario que fija el convenio, con su artículo.
- Calcula lo que te deben este mes comparando tu horario con lo que fichaste.
- Calculadora de horas extra según tu convenio, con sus fuentes.
- Registro de jornada sencillo, con aviso para fichar. A los 14 días el diario se sella con fecha del servidor, lo que hace que tu libreta sea difícil de discutir.
- Guarda tus cuadrantes con fecha, para que quede constancia de los cambios de turno de última hora.
- Exporta un informe PDF del mes o del año, con tus registros y el detalle de cada cálculo, listo para llevar a un sindicato o a un abogado.

Dentro hay 55 convenios de hostelería: las 50 provincias más Ceuta y Melilla, transcritos de los boletines oficiales. Cada cifra lleva su artículo y el enlace al boletín. Si un dato no está publicado, la app lo dice; no se lo inventa.

Los cálculos son orientativos. Salen de las tablas de tu convenio, pero pueden tener errores o no encajar con tu caso concreto. No es asesoramiento jurídico: antes de reclamar, contrástalo con tu sindicato, un abogado laboralista o la Inspección de Trabajo.

Sobre tus datos:

- Es gratis. No pagas por usarla, y lo que hoy es gratis no pasará a ser de pago.
- No vendemos ni cedemos tus datos a nadie. No hay anuncios ni rastreadores.
- Son tuyos: puedes borrar la cuenta y todo lo que contiene cuando quieras, desde la app o en medeben.net/borrar-cuenta.
- Solo pedimos tu email y los datos laborales que tú indicas. Ni nombre, ni DNI, ni teléfono. Si activas «Anotar dónde fichas», guardamos también tu posición aproximada al fichar — opcional, apagada de fábrica, y la desactivas y borras cuando quieras.
- El código es público (AGPL-3.0): cualquiera puede comprobar qué hace la app con tus datos.

MeDeben está pensada para personas trabajadoras mayores de edad.
```

---

## Alternativas de título (todas ≤ 30 car.)

| Alternativa | Car. | Nota |
|---|---|---|
| `MeDeben: horas y convenio` | 25 | **Elegida.** Las dos búsquedas más probables. |
| `MeDeben: tu convenio y horas` | 28 | Pone el convenio primero; más cercana. |
| `MeDeben: fichar y horas extra` | 29 | Acento en fichaje + horas extra. |
| `MeDeben: registro de jornada` | 28 | La más literal/funcional. |
| `MeDeben` | 7 | Solo marca, comodín seguro. |

## Alternativas de descripción corta (todas ≤ 80 car.)

| Alternativa | Car. | Nota |
|---|---|---|
| `Ficha tus horas, calcula lo que te deben y consulta tu convenio de hostelería.` | 78 | **Elegida.** Las tres acciones clave. |
| `Registra tu jornada y calcula las horas extra según tu convenio. Gratis.` | 72 | Cierra con "gratis". |
| `Tu convenio de hostelería y tus horas, en claro. Sin vender tus datos.` | 70 | Acento en privacidad; retoma el claim del README. |
| `Apunta tus horas y mira lo que te deben según tu convenio. Orientativo.` | 71 | Deja claro desde ya que es orientativo. |

---

## Assets que faltan (con especificaciones exactas de Play)

Estos tres bloques de imágenes **no existen todavía** en el repo y son necesarios
para publicar la ficha (research §7 + sección B):

1. **Icono de la app — 512×512 px.**
   - Formato: **PNG de 32 bits con alpha**. Tamaño máximo: **1024 KB**.
   - Ojo: **no** reutilizar `pwa-512x512.png` tal cual (trae esquinas ya
     redondeadas/transparentes y Play aplica su propia máscara encima).
     Exportar un **cuadrado a sangre** desde `frontend/assets/icon-*.svg`.

2. **Feature graphic — 1024×500 px.**
   - Formato: **JPEG o PNG de 24 bits SIN alpha** (sin transparencia).
   - Marca: fondo navy, acento teal, símbolo euro/"E", "MeDeben" + claim corto.

3. **Capturas de teléfono — mínimo 2, máximo 8.**
   - Dimensión: entre **320 px y 3840 px**; el lado mayor **no puede superar el
     doble del menor**.
   - Recomendable 4–8 pantallas clave: el cálculo "te deben X€", el registro de
     jornada, el informe PDF y el perfil/convenio.
   - Incluir el aviso "orientativo" visible en la captura del cálculo.

> Todos los assets: entregar en `frontend/` y **copia** en
> `C:\Users\iulia\Pictures\` (regla de entregables).
