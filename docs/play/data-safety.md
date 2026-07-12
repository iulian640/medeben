# Data Safety (Seguridad de los datos) — borrador para Play Console

> Respuestas listas para copiar al cuestionario **"Seguridad de los datos"** de
> Google Play Console, pregunta por pregunta. Cada respuesta lleva una **nota**
> de por qué se contesta así.
>
> **Coherencia obligatoria:** este formulario debe cuadrar al 100 % con la
> política de privacidad publicada (`docs/legal/privacidad.md`, v1.0) y con la
> tabla de medidas técnicas (`docs/cumplimiento-lanzamiento.md`, filas A1–A7).
> Si cambias uno, cambia el otro.
>
> Fuente del formato: https://support.google.com/googleplay/android-developer/answer/10787469
>
> **AVISO IMPORTANTE sobre el cifrado:** Play solo pregunta por **cifrado en
> tránsito** (una sola pregunta global). **No** hay pregunta de "cifrado en
> reposo". El campo `motivo` **NO está cifrado en reposo** todavía (C4 pendiente,
> ver `cumplimiento-lanzamiento.md`). No declares ni insinúes en ningún sitio que
> el `motivo` se guarda cifrado hasta que C4 esté hecho.

---

## Bloque 0 — Preguntas globales del cuestionario

**P0.1 · ¿Tu app recoge o comparte alguno de los tipos de datos de usuario requeridos?**
- **Respuesta: SÍ.**
- Nota: la app recoge email, perfil laboral, jornada y el motivo de ausencia y
  los transmite a su propio servidor (Hetzner, UE). "Recoger" en Play = sacar
  datos del dispositivo, aunque sea a tu propio backend. Por tanto: Sí.

**P0.2 · ¿Todos los datos de usuario que recoge la app están cifrados en tránsito?**
- **Respuesta: SÍ.**
- Nota: HTTPS/TLS obligatorio (Caddy), HSTS activo, backend solo accesible por
  127.0.0.1 detrás del proxy (fila A6 de cumplimiento). No hay tráfico en claro.
- Recordatorio: esta pregunta es SOLO sobre tránsito. No implica nada sobre el
  cifrado en reposo (que Play no pregunta y que para el `motivo` aún no existe).

**P0.3 · ¿Ofreces a los usuarios una forma de solicitar que se borren sus datos?**
- **Respuesta: SÍ.**
- Nota: hay dos vías, como exige Play:
  1. **In-app:** `DELETE /api/v1/cuenta` — borrado real con `ON DELETE CASCADE`,
     re-confirma contraseña y purga el PDF cacheado (fila A2).
  2. **Web (sin la app):** https://medeben.net/borrar-cuenta
- **URL de solicitud de borrado a declarar:** `https://medeben.net/borrar-cuenta`
- Fuente del requisito de borrado: https://support.google.com/googleplay/android-developer/answer/13327111

---

## Bloque 1 — Tipos de datos: qué marcar como RECOGIDO

Para **cada** tipo de dato recogido, Play pide siempre las mismas 4 sub-respuestas:
- **¿Recogido o compartido?** → En MeDeben, siempre **Recogido, NO compartido**
  (ver Bloque 3: no hay terceros; Hetzner es encargado, no cuenta como "compartir").
- **¿Se procesa de forma efímera?** → **No** (todo se almacena en el servidor).
- **¿Es obligatorio u opcional?** → indicado en cada caso.
- **Finalidad(es)** → indicada en cada caso.

### 1.1 · Información personal → Dirección de correo electrónico
- **Marcar:** SÍ, recogido.
- **Compartido:** No.
- **Efímero:** No.
- **Obligatorio / opcional:** **Obligatorio** (required).
- **Finalidad:** *Gestión de la cuenta* (Account management) + *Funcionamiento
  de la app* (App functionality).
- Nota: el email es el identificador de la cuenta; sin él no hay registro
  (privacidad §2). Se guarda tal cual; la contraseña va aparte y solo como hash,
  así que la contraseña NO se declara como dato recogido (Play no considera dato
  recogido una credencial que solo se usa para autenticar y se guarda hasheada).

### 1.2 · Información personal → Otra información personal (perfil laboral)
- **Marcar:** SÍ, recogido. (Categoría: *Personal info > Other info*.)
- **Cubre:** provincia, convenio, puesto y nivel.
- **Compartido:** No.
- **Efímero:** No.
- **Obligatorio / opcional:** **Obligatorio** (required).
- **Finalidad:** *Funcionamiento de la app* (App functionality).
- Nota: son los datos laborales que el usuario indica (privacidad §2). Sin
  provincia + tipo de establecimiento no se puede localizar el convenio, que es
  la función nuclear de la app; por eso se declara como obligatorio. **No es
  ubicación del dispositivo:** la provincia la teclea el usuario, no viene del
  GPS, así que **NO** se marca la categoría "Ubicación".

### 1.3 · Información financiera → Otra información financiera (salario)
- **Marcar:** SÍ, recogido. (Categoría: *Financial info > Other financial info*.)
- **Compartido:** No.
- **Efímero:** No.
- **Obligatorio / opcional:** **Opcional** (optional).
- **Finalidad:** *Funcionamiento de la app* (App functionality).
- Nota: Play mete explícitamente el "salario del usuario" en *Otra información
  financiera*. Es el salario que el usuario indica (privacidad §2). Se marca
  **opcional** porque se puede consultar el convenio y registrar horas sin
  introducir el salario propio; solo alimenta la estimación de "lo que te deben".
  No se recoge ningún dato de pago, tarjeta ni historial de compras.

### 1.4 · Actividad en la app → Otro contenido generado por el usuario (jornada)
- **Marcar:** SÍ, recogido. (Categoría: *App activity > Other user-generated content*.)
- **Cubre:** fichajes de entrada/salida, horario/cuadrante y ausencias.
- **Compartido:** No.
- **Efímero:** No.
- **Obligatorio / opcional:** **Opcional** (optional).
- **Finalidad:** *Funcionamiento de la app* (App functionality).
- Nota: son registros que el usuario crea al usar el registro de jornada
  (privacidad §2). Es opcional porque se puede usar la app (consulta de convenio,
  salario por puesto) sin registrar ni una hora. No es "App interactions" ni
  analítica: es contenido que teclea el propio usuario.

### 1.5 · Salud y forma física → Información de salud (motivo de ausencia)
- **Marcar:** SÍ, recogido. (Categoría: *Health and fitness > Health info*.)
- **Compartido:** No.
- **Efímero:** No.
- **Obligatorio / opcional:** **Opcional** (optional).
- **Finalidad:** *Funcionamiento de la app* (App functionality).
- **Nota (la más importante del formulario):** el `motivo` es un texto libre
  opcional (máx. 200 car.) que el usuario puede escribir al registrar una
  ausencia. **Puede revelar un dato de salud** (p. ej. "enfermo"). La política de
  privacidad ya lo trata como **categoría especial del art. 9 RGPD** con base
  **9.2.f** (defensa de reclamaciones). Play **NO tiene exención** para datos de
  salud: si se recoge, se declara. Por eso se marca *Health info*, aunque sea
  incidental y opcional.
- **Por qué se declara pero NO convierte a MeDeben en "app de salud":** la Health
  apps policy de Play apunta a apps cuya funcionalidad **es** la salud o que
  procesan datos de salud reales; un campo de texto libre incidental no lo es
  (ver research, punto 10). Estrategia: declararlo honestamente aquí, marcarlo
  **opcional**, y **no** venderlo como funcionalidad de salud en la ficha.
- **Recordatorio de cifrado:** no marques nada que insinúe cifrado en reposo del
  motivo. Play no lo pregunta y aún no existe (C4).

---

## Bloque 2 — Tipos de datos que NO se recogen (marcar "No")

Marca explícitamente **NO recogido / NO compartido** en todo lo demás. Sirve de
checklist para no dejarte ninguna casilla:

| Categoría Play | ¿Se recoge? | Por qué |
|---|---|---|
| Ubicación (aproximada o precisa del dispositivo) | **No** | Sin permiso de ubicación en el manifest (solo INTERNET). La provincia la teclea el usuario, no el GPS. |
| Información personal → Nombre | **No** | No se pide nombre (privacidad §2). |
| Información personal → Nº de teléfono | **No** | No se pide teléfono. |
| Información personal → Dirección física | **No** | No se pide. |
| Información personal → Raza/etnia, ideología, orientación, etc. | **No** | No se piden. |
| Información financiera → Info de pago / historial de compras / solvencia | **No** | No hay pagos ni compras in-app. |
| Mensajes (SMS, email, in-app) | **No** | No hay mensajería. |
| Fotos y vídeos | **No** | No se recogen (la foto de cuadrante NO está implementada). |
| Archivos de audio | **No** | No aplica. |
| Archivos y documentos | **No** | El PDF se genera y se descarga en el dispositivo; no se sube contenido de archivos del usuario. |
| Calendario | **No** | No aplica. |
| Contactos | **No** | No aplica. |
| Actividad en la app → Historial de búsqueda / apps instaladas | **No** | No se recogen. |
| Historial de navegación web | **No** | No aplica. |
| Info y rendimiento de la app → Registros de fallos / diagnóstico | **No** | No hay SDK de crashes ni analítica de terceros (privacidad §2). |
| Identificadores → ID de dispositivo o de publicidad | **No** | No se usa Advertising ID ni analítica; nada de tracking. |

Nota general: la coartada técnica es el `AndroidManifest.xml`, que declara
**solo el permiso INTERNET**. No hay cámara, ubicación, almacenamiento ni
identificadores de publicidad que declarar.

---

## Bloque 3 — Datos COMPARTIDOS con terceros

**Respuesta global: NO se comparte ningún dato con terceros.**

- Nota: Play define "compartir" como transferir datos a un **tercero**, y
  **excluye explícitamente** la transferencia a un **proveedor que procesa por tu
  cuenta** (service provider / encargado de tratamiento). **Hetzner** aloja los
  datos como **encargado de tratamiento** (art. 28 RGPD, servidores en Alemania,
  UE) → **no** cuenta como "compartir" y **no** se declara aquí.
- No hay analítica de terceros, ni publicidad, ni venta/cesión de datos
  (privacidad §2 y §6: "No vendemos ni cedemos tus datos a nadie").

---

## Bloque 4 — Borrado de datos (sección específica del formulario)

- **¿Los usuarios pueden pedir que se borren sus datos?** → **SÍ**.
- **URL de solicitud de borrado:** `https://medeben.net/borrar-cuenta`
  - Debe estar **publicada y funcional** antes de enviar el formulario, y permitir
    pedir el borrado **sin volver a entrar en la app** (aunque se haya
    desinstalado). El endpoint ya existe; la página web es el requisito B3.
- **Borrado también disponible dentro de la app:** SÍ (ruta in-app que llama a
  `DELETE /api/v1/cuenta`). Play exige **ambas** vías para apps con cuenta.
- Nota: esta misma URL de borrado se declara además en **App content → Account
  deletion** de Play Console (es un requisito aparte pero con la misma página).

---

## Resumen para verificación cruzada (Data Safety ⇄ privacidad.md)

| Dato | Categoría Play | Oblig./Opc. | Finalidad | Base RGPD (privacidad) |
|---|---|---|---|---|
| Email | Personal info → Email | Obligatorio | Cuenta + Funcionamiento | art. 6.1.b |
| Provincia/convenio/puesto | Personal info → Otra | Obligatorio | Funcionamiento | art. 6.1.b |
| Salario | Financial info → Otra financiera | Opcional | Funcionamiento | art. 6.1.b |
| Jornada (fichajes/horario/ausencias) | App activity → Contenido del usuario | Opcional | Funcionamiento | art. 6.1.b |
| Motivo de ausencia | Health → Health info | Opcional | Funcionamiento | art. 9.2.f |
| Cifrado en tránsito | — | — | — | Sí (A6) |
| Borrado | — | — | — | Sí, in-app + URL (A2) |
| Compartir con terceros | — | — | — | No (§6) |
