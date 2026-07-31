# Backend

Java 21 and Spring Boot backend for the InvoiceNow Reconciliation Workbench.

## Toolchain

- Java 21
- Spring Boot 4.1.0
- Maven Wrapper 3.3.4 using Maven 3.9.16
- PostgreSQL 18.4
- Flyway
- Testcontainers 2.x through Spring Boot dependency management

The wrapper is authoritative; a system Maven installation is not required.

## Verify

From this directory:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

On Unix-like systems:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

Integration tests require a Docker-compatible container runtime because they start a real PostgreSQL 18.4 container. They do not substitute H2 for PostgreSQL.

## Run locally

From the repository root, start PostgreSQL:

```powershell
docker compose up -d postgres
```

Then start the API:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Public health endpoints:

- `GET http://localhost:8080/api/v1/health/public`
- `GET http://localhost:8080/actuator/health`

All other routes are authenticated by default. The production/demo identity design is intentionally deferred to the dedicated identity phase.

## Configuration

The application reads these environment variables with local-only defaults:

- `POSTGRES_HOST`
- `POSTGRES_PORT`
- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `SERVER_PORT`

Never commit real passwords. Copy the root `.env.example` to `.env` only for local development.
