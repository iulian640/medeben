# TeDeben — backend

API REST en Java 21 + Spring Boot 3 (Maven). Paquete base: `es.tedeben`.

## Estructura

```
src/main/java/es/tedeben/
├── config/       # Configuración (Spring Security, etc.)
├── controller/   # Capa web (REST)
├── service/      # Lógica de negocio
├── repository/   # Acceso a datos (Spring Data JPA)
├── domain/       # Entidades y modelo de dominio
└── dto/          # Objetos de transferencia (request/response)
```

Migraciones de esquema con Flyway en `src/main/resources/db/migration/`.

## Arrancar en local

Requiere PostgreSQL local (hay un `docker-compose.yml` en la raíz del repo):

```bash
docker compose up -d          # desde la raíz del repo
cd backend
mvn spring-boot:run           # perfil por defecto: dev
```

Comprobación: `GET http://localhost:8080/api/v1/health` → `{"status":"ok"}`.

## Tests

```bash
mvn test
```

- Los tests de slice (MockMvc) corren siempre, sin base de datos.
- **Los tests de integración requieren Docker**: usan Testcontainers con
  PostgreSQL 16 real (H2 no se usa nunca — mismo motor que producción).
  Sin Docker se saltan automáticamente (`disabledWithoutDocker = true`)
  y `mvn test` sigue en verde.

## Notas

- El JDK local puede ser más nuevo (26); el proyecto compila con
  `--release 21` (Java 21 LTS) para compatibilidad con Spring Boot.
- Seguridad: `/api/v1/health` y `/actuator/health` son públicos; el resto
  requiere autenticación. JWT llegará después (ADR D13.4).

Ver [docs/dev-setup.md](../docs/dev-setup.md) para el setup completo.
