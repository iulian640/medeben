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

**HTTPS (pendiente de dominio):** el compose expone el puerto 80. Con dominio
comprado, lo limpio es poner delante Caddy (TLS automático) o certbot. Sin
HTTPS no se debe abrir a usuarios reales: el JWT viajaría en claro.

## 2. APK de Android

```bash
cd frontend && npm run build && npx cap sync android
cd android && JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleRelease
# → android/app/build/outputs/apk/release/app-release.apk (FIRMADO)
```

- La firma sale de `frontend/android/keystore.properties` (gitignorado).
- El keystore vive en `C:\Users\iulia\Documents\medeben-release\` — **haz
  copia de seguridad de esa carpeta** (instrucciones en su LEEME.txt).
- La app apunta al API con `VITE_API_URL` en tiempo de build: para producción,
  compilar con `VITE_API_URL=https://<tu-dominio>` antes del `cap sync`.

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
