# Levantar TeDeben en local

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
`tedeben` y volumen persistente (`tedeben-pgdata`). Para pararlo:
`docker compose down` (los datos se conservan).

## 2. Backend (Spring Boot)

```bash
cd backend
mvn spring-boot:run        # perfil por defecto: dev (postgres local)
```

Comprobación: <http://localhost:8080/api/v1/health> → `{"status":"ok"}`.

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
