# Running MeDeben locally

> Lee esto en español → [dev-setup.es.md](dev-setup.es.md)

## Requirements

| Tool | Version | What for |
|---|---|---|
| JDK | 21+ (the project compiles with `--release 21`) | backend |
| Maven | 3.9+ | backend |
| Node.js | 20+ | frontend |
| Docker | any recent one | local PostgreSQL and integration tests |
| gh (GitHub CLI) | optional | PRs |

## 1. Database (PostgreSQL 16)

From the repo root:

```bash
docker compose up -d
```

Starts a PostgreSQL 16 on `localhost:5432` with `medeben` as database, user
and password, and a persistent volume (`medeben-pgdata`). To stop it:
`docker compose down` (the data is kept).

## 2. Backend (Spring Boot)

```bash
cd backend
mvn spring-boot:run        # activates the "dev" profile (local postgres)
```

Check: <http://localhost:8080/api/v1/health> → `{"status":"ok"}`.

`mvn spring-boot:run` explicitly activates the `dev` Spring profile (configured
in `backend/pom.xml`'s `spring-boot-maven-plugin`). This is required: the JWT
config (`JwtConfig`) refuses to start with the toy secrets checked into the
repo unless an active profile of `dev`, `local`, or `test` is present —
`spring.profiles.default=dev` alone does **not** count as active, on purpose,
so that a deployment started without `SPRING_PROFILES_ACTIVE` fails fast
instead of silently signing tokens with a public secret.

To run with a different profile (e.g. `local`, against a different DB setup):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

or set `SPRING_PROFILES_ACTIVE` in the environment before running — either
one overrides the `dev` profile baked into the plugin configuration.

Tests:

```bash
mvn test
```

Integration tests use Testcontainers (real PostgreSQL, no H2) and **require
Docker**; without Docker they skip themselves and the build stays green.

## 3. Frontend (Vue 3 + Vite)

```bash
cd frontend
npm install
npm run dev                # http://localhost:5173
```

The dev server proxies `/api` → `http://localhost:8080`, so start the backend
first if you want real calls.

Production build (type-check included):

```bash
npm run build
```

## Full sequence

```bash
docker compose up -d                      # 1. DB
(cd backend && mvn spring-boot:run) &     # 2. API on :8080
cd frontend && npm run dev                # 3. Web on :5173
```
