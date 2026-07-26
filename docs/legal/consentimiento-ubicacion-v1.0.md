# Consentimiento de ubicación — v1.0 (texto canónico, inmutable)

> **Fichero inmutable.** Este texto es la versión `1.0` del consentimiento de la
> función «Anotar dónde fichas» (ADR D39). El backend guarda, por cada
> aceptación, la referencia `version_texto = '1.0'` junto al `SHA-256` de este
> texto exacto (calculado en servidor), para poder acreditar ante la AEPD (art.
> 7.1 RGPD) qué se consintió y cuándo.
>
> **Si el texto cambia**, se publica una versión nueva (`v1.1`, `v2.0`...) en un
> fichero nuevo — este NUNCA se edita después de publicado. Editarlo rompería
> el hash que el backend tiene fijado en sus tests y en las filas ya guardadas
> de `consentimientos_ubicacion`.
>
> Debe reproducirse **literal**, carácter a carácter, en la pantalla de
> activación del frontend (`PanelUbicacion.vue`) y en la constante versionada
> del backend.

```
Anotar dónde fichas — consentimiento (v1.0)

Si activas esta opción, cuando fiches al momento la app anotará tu posición
aproximada (precisión de barrio, no de portal) junto a ese fichaje.

- Finalidad: acompañar tu registro horario con una anotación de que estabas en tu
  centro de trabajo, para reforzar la coherencia de tu libreta.
- Base jurídica: tu consentimiento inequívoco y expreso (art. 6.1.a RGPD). La app
  funciona entera sin esto.
- Qué se envía y a dónde: la posición aproximada sale de tu dispositivo y se guarda
  en el servidor de MeDeben, alojado en la Unión Europea. La resolución de la
  posición aproximada la realiza el proveedor de ubicación de tu sistema operativo
  (Google Play Services) según su propia política.
- Qué NO se hace: nada en segundo plano, nunca se te sigue, solo se anota en el
  instante en que tú fichas. Tus coordenadas no aparecen en el informe que se
  entrega a terceros; solo en un anexo que controlas tú.
- Conservación: hasta 15 meses desde cada fichaje, salvo que declares una
  reclamación en curso. Después se borra; tu fichaje permanece.
- Puedes retirar este consentimiento en cualquier momento con un toque, sin que
  afecte a nada de lo demás, y borrar todo tu histórico de ubicaciones cuando
  quieras. Retirarlo no afecta a la licitud del tratamiento previo.
- Derechos: acceso, rectificación, supresión y portabilidad escribiendo al
  responsable; también puedes reclamar ante la AEPD (aepd.es).
- Importante: tu empresa no puede exigirte activar esto ni entregarle el anexo con
  tus coordenadas. Si te lo piden, eso es control por geolocalización y debe cumplir
  el art. 90 de la LOPDGDD.
```
