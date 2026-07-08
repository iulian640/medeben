# App Android (Capacitor): compilar e instalar el APK

La PWA de `frontend/` va envuelta con [Capacitor](https://capacitorjs.com/) para
tener app nativa Android — la razón de entrar ya en esta fase son las
notificaciones locales de la libreta sellada (D38: recordatorios de fichaje y
contador de sellado). El proyecto nativo vive en `frontend/android/` y **se
commitea** (convención de Capacitor); lo generado en cada build (assets web
copiados, `build/`, `local.properties`) queda gitignored.

## Requisitos

| Herramienta | Versión | Notas |
|---|---|---|
| Node.js | 20+ | mismo que la web |
| Android SDK | platform 36 + build-tools | con Android Studio ya lo tienes |
| JDK **21** | para Gradle | vale el JBR de Android Studio (`C:\Program Files\Android\Android Studio\jbr`); los JDK 25/26 NO — Gradle 8.14 no corre sobre ellos |

Versiones fijadas por el proyecto generado: Capacitor 8.4.1, AGP 8.13.0,
Gradle 8.14.3 (wrapper), `minSdk` 24, `compileSdk`/`targetSdk` 36.

## Setup una sola vez

1. Dile a Gradle dónde está el SDK creando `frontend/android/local.properties`
   (gitignored, es de tu máquina):

   ```properties
   sdk.dir=C\:\\Users\\<tu-usuario>\\AppData\\Local\\Android\\Sdk
   ```

2. Apunta la app al backend. La app envuelta NO pasa por el proxy de Vite
   (eso es solo `npm run dev`): la URL del backend se hornea en el build vía
   `VITE_API_URL`. Copia la plantilla y pon la IP de tu PC en la LAN:

   ```bash
   cd frontend
   cp .env.capacitor.example .env.capacitor   # y edita la IP
   ```

   El móvil y el PC deben estar en la misma red, y el backend arrancado con
   `mvn spring-boot:run` escuchando en `0.0.0.0` (Spring lo hace por defecto).
   Quizá tengas que abrir el puerto 8080 en el firewall de Windows.

## Compilar el APK (cada vez)

```bash
cd frontend
npm run build -- --mode capacitor   # build web con VITE_API_URL de .env.capacitor
npx cap sync android                # copia dist/ al proyecto nativo y sincroniza plugins
cd android
gradlew.bat assembleDebug           # en Linux/macOS: ./gradlew assembleDebug
```

Si Gradle protesta por la versión de Java, fuerza el JDK 21 en esa terminal:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
```

El APK sale en:

```text
frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

## Instalarlo en el móvil

- **Con cable (adb):** activa la depuración USB en el móvil y

  ```bash
  adb install -r frontend/android/app/build/outputs/apk/debug/app-debug.apk
  ```

  (`adb` está en `<SDK>/platform-tools/`; `-r` reinstala conservando datos).

- **Sin cable:** copia el `app-debug.apk` al móvil (Quick Share, cable, lo que
  sea), ábrelo y acepta instalar de origen desconocido. Es un APK de debug
  firmado con la clave de debug: Android avisará, es normal.

## HTTP en claro: solo en debug

Android bloquea el tráfico HTTP sin TLS desde Android 9. Para poder hablar con
el backend de la LAN (`http://192.168.1.XX:8080`) el build de **debug** lo
permite vía `android:usesCleartextTraffic="true"` en
`frontend/android/app/src/debug/AndroidManifest.xml`.

Ese manifest solo se fusiona en builds de debug. **No lo muevas a `src/main`
ni lo repliques en release**: la app maneja datos laborales sensibles (RGPD)
y en producción todo el tráfico va cifrado (HTTPS). Un release nunca debe
poder degradarse a HTTP en claro.

## Notificaciones locales

El plugin `@capacitor/local-notifications` (8.2.0) ya está instalado y
sincronizado; sus permisos en el manifest los gestiona el propio plugin. La
base TS probada está en `frontend/src/lib/notificaciones.ts` (pedir permiso +
programar). La UX (cuándo y qué notificar) llega con la pantalla Hoy — no
cablear nada antes.

## Avisos conocidos del build

- Los iconos del launcher son los placeholders azules de la PWA (pendiente de
  diseño); el APK arranca con ellos.
- Gradle deja un "Problems report" HTML con avisos de deprecación del propio
  template de Capacitor (nada nuestro); desaparecerán con futuras versiones
  de Capacitor/AGP.
- El primer build descarga el wrapper de Gradle y dependencias (varios
  minutos); los siguientes son incrementales.
