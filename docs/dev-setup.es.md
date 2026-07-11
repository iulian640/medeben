# Levantar MeDeben en local

> Read this in English → [dev-setup.md](dev-setup.md)

## Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | 21+ (el proyecto compila con `--release 21`) | backend |
| Maven | 3.9+ | backend |
| Node.js | 20+ | frontend |
| Docker | cualquiera reciente | PostgreSQL local y tests de integración |
| gh (GitHub CLI) | opcional | PRs |

## 1. Base de datos (PostgreSQL 16)

Desde la raíz del repo:

```bash
docker compose up -d
```

Levanta un PostgreSQL 16 en `localhost:5432` con BD/usuario/contraseña
`medeben` y volumen persistente (`medeben-pgdata`). Para pararlo:
`docker compose down` (los datos se conservan).

## 2. Backend (Spring Boot)

```bash
cd backend
mvn spring-boot:run        # activa el perfil "dev" (postgres local)
```

Comprobación: <http://localhost:8080/api/v1/health> → `{"status":"ok"}`.

`mvn spring-boot:run` activa explícitamente el perfil de Spring `dev`
(configurado en el `spring-boot-maven-plugin` de `backend/pom.xml`). Es
necesario: la config de JWT (`JwtConfig`) rechaza arrancar con los secretos de
juguete del repo salvo que haya un perfil ACTIVO `dev`, `local` o `test` —
`spring.profiles.default=dev` por sí solo NO cuenta como activo, a propósito,
para que un despliegue arrancado sin `SPRING_PROFILES_ACTIVE` falle rápido en
vez de firmar tokens en silencio con un secreto público.

Para arrancar con otro perfil (p. ej. `local`, con otra configuración de BD):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Fijar `SPRING_PROFILES_ACTIVE` en el entorno **no** sirve para esto: el
`spring-boot-maven-plugin` convierte el perfil `dev` de su `<configuration>`
en un argumento de programa `--spring.profiles.active=dev` en la JVM
lanzada, y en el orden de precedencia de Spring Boot los argumentos de línea
de comandos ganan a las variables de entorno del sistema operativo — así que
el `dev` del plugin se impone en silencio sobre `SPRING_PROFILES_ACTIVE`
(comprobado: la app registra `"dev"` como activo en ambos casos). Usa
`-Dspring-boot.run.profiles=<nombre>` —sustituye el propio `<profiles>` del
plugin— para cambiar de perfil de verdad.

Tests:

```bash
mvn test
```

Los tests de integración usan Testcontainers (PostgreSQL real, sin H2) y
**requieren Docker**; sin Docker se saltan solos y la build sigue verde.

## 3. Frontend (Vue 3 + Vite)

```bash
cd frontend
npm install
npm run dev                # http://localhost:5173
```

El dev server proxya `/api` → `http://localhost:8080`, así que arranca el
backend antes si quieres llamadas reales.

Build de producción (type-check incluido):

```bash
npm run build
```

## Orden completo

```bash
docker compose up -d                      # 1. BD
(cd backend && mvn spring-boot:run) &     # 2. API en :8080
cd frontend && npm run dev                # 3. Web en :5173
```
