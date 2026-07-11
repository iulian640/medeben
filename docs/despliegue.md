# Desplegar MeDeben

Todo lo que hace falta para poner MeDeben en producción, en orden. Escrito el
2026-07-09 al preparar el release; los pendientes que dependen de decisiones
de Iulian están marcados.

## 1. Web + API (docker compose)

En el servidor (cualquier VPS con Docker):

```bash
git clone https://github.com/iulian640/medeben && cd medeben

# Los dos únicos secretos, en deploy/.env (NUNCA en el repo):
cat > deploy/.env << 'FIN'
MEDEBEN_DB_PASSWORD=<contraseña larga>
MEDEBEN_SEGURIDAD_JWT_SECRETO=<openssl rand -base64 48>
FIN

# La PWA compilada que servirá nginx:
cd frontend && npm ci && npm run build && cd ..

docker compose -f deploy/docker-compose.prod.yml up -d --build
```

Qué levanta: PostgreSQL 16 (volumen persistente), el backend (perfil `prod`,
migraciones Flyway al arrancar, rate limiting con `confiar-en-proxy=true`) y
nginx sirviendo la PWA con las **cabeceras de seguridad de la auditoría**
(HSTS, CSP, nosniff, frame-deny, referrer, permissions) y el proxy de `/api`.

**HTTPS es OBLIGATORIO antes de abrir a usuarios (no es opcional).** El servicio
`web` del compose escucha en HTTP plano y se publica solo en `127.0.0.1:80`
—accesible para un proxy TLS en el mismo host, no desde fuera— justamente para
que nadie lo exponga sin cifrar por error. La app manda usuario+contraseña y el
JWT (válido 24 h) en cada petición; sin TLS, cualquiera en el WiFi del local
(público, compartido) hace un MITM y los captura en claro. La cabecera HSTS que
emite nginx SOLO surte efecto una vez servido por HTTPS.

**Backups de la BD (obligatorio antes de usuarios reales):** ver
[backups.md](backups.md) — `deploy/backup-db.ps1` diario programado con copia
externa, y `deploy/restaura-db.ps1` para ensayar la restauración. La BD guarda
la evidencia de los usuarios; sin backup verificado no hay producción.

### HTTPS con Caddy (dominio medeben.net)

Hay un **`deploy/Caddyfile`** listo. En el servidor, con Caddy instalado:

```bash
# DNS primero: registro A (y AAAA si hay IPv6) de medeben.net Y de
# www.medeben.net apuntando a la IP del VPS. www necesita resolver aunque solo
# redirija (Caddy le saca su propio certificado).
sudo cp deploy/Caddyfile /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

Caddy saca el certificado de Let's Encrypt solo, redirige `80→443` y `www→apex`,
y proxya al nginx en `127.0.0.1:80`. Requiere los puertos **80 y 443** abiertos
en el firewall (el 80 hace falta para el reto ACME, no solo para redirigir).

**Rate limiting detrás de Caddy (no romperlo):** el `nginx.conf` reenvía
`X-Forwarded-For` tal cual (`$http_x_forwarded_for`), NO con
`$proxy_add_x_forwarded_for`. Con Caddy delante, el peer inmediato de nginx es
Caddy (127.0.0.1); anexar ese salto machacaría la IP real y metería a todos los
clientes en una única cubeta. Caddy debe conservar su `trusted_proxies` por
defecto (vacío) para que la última entrada de X-Forwarded-For sea siempre la IP
real del cliente. (Ya está así en el repo; se documenta para que nadie lo
revierta "arreglando" la config de nginx.)

Hasta que HTTPS esté, la app no debe tener usuarios reales.

## 2. APK de Android

`VITE_API_URL` DEBE apuntar a `https://medeben.net` **en el build** (el APK no
comparte origen con el API). La variable de entorno del shell gana a cualquier
`.env.capacitor` (que tiene el valor del emulador, `http://10.0.2.2`), así que
basta exportarla; no toques el `.env`. En PowerShell:

```powershell
cd frontend
$env:VITE_API_URL = "https://medeben.net"
npm run build
npx cap sync android
cd android
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"
./gradlew assembleRelease
# → android/app/build/outputs/apk/release/app-release.apk (FIRMADO)
```

- La firma sale de `frontend/android/keystore.properties` (gitignorado).
- El keystore vive en `C:\Users\iulia\Documents\medeben-release\` — **haz
  copia de seguridad de esa carpeta** (instrucciones en su LEEME.txt).
- **CORS (imprescindible para que el APK funcione):** el WebView de Capacitor
  vive en `https://localhost` y llama a `https://medeben.net/api` (cross-origin).
  El backend lo permite (bean CORS en `SecurityConfig`). La lista por defecto
  incluye `https://localhost` (APK), `https://medeben.net` (web de prod) y los
  `localhost:4180/5173` de dev/E2E. **OJO:** al habilitar CORS, Spring valida
  también las peticiones same-origin que llevan cabecera `Origin` (los POST),
  así que el origen de la **web** debe estar en la lista o el propio registro/login
  se rechazaría con 403 — por eso `medeben.net` va incluido. Para iOS
  (`capacitor://localhost`) u otro dominio, añádelo con
  `MEDEBEN_SEGURIDAD_CORS_ORIGENES` (lista separada por comas) sin tocar código.
- El mismo `dist/` (compilado con `VITE_API_URL=https://medeben.net`) sirve para
  la PWA web de nginx: al ser same-origin, la URL absoluta funciona igual.

## 3. Play Store (todo pendiente de Iulian)

1. Cuenta de desarrollador de Google Play (25 USD, una vez).
2. Para subir a Play se recomienda AAB: `./gradlew bundleRelease` (misma firma).
3. Ficha de la app: nombre MeDeben, capturas, descripción (usar la skill
   `avoid-ai-writing` para el copy).
4. **Política de privacidad** (obligatoria: la app trata datos laborales y
   motivos de ausencia, art. 9 RGPD) publicada en una URL.
5. Cuestionario de seguridad de datos de la consola de Play.

## Decisiones aún abiertas (de la auditoría)

- Bloqueo por cuenta como defensa fina anti fuerza bruta (el rate limiting por
  IP ya está; esto es la capa siguiente).
- PK UUIDv4 en tablas append-only (decisión de diseño).
- Id de tramo/apunte en el API para correcciones dirigidas (decisión de producto).
- Qué hacer con los docs personales del repo público.
