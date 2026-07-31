# Application Foundation Verification

Status: Local verification passed; pull-request CI pending

Issues: `IRW-005`, `IRW-006`, `IRW-007`

Verification date: 31 July 2026

## Verified toolchain

| Component | Verified version |
|---|---|
| Java | Eclipse Temurin 21.0.11 in the verification container |
| Spring Boot | 4.1.0 |
| Maven Wrapper | 3.3.4 |
| Maven distribution | 3.9.16 |
| PostgreSQL | 18.4 Alpine image |
| Testcontainers | 2.0.5 through Spring Boot dependency management |
| Docker Engine | 29.5.3 through Docker Desktop |

The development machine did not have a host Java or Maven installation. The authoritative local check therefore used the committed Maven Wrapper inside a Java 21 container and connected Testcontainers to the existing Docker engine.

## Quality command

The repository-equivalent command remains:

```text
cd backend
./mvnw --batch-mode --no-transfer-progress verify
```

For the Windows host without Java, the same wrapper goal was executed from an Eclipse Temurin 21 container with the repository and Docker socket mounted. The GitHub Actions workflow runs the repository-equivalent command directly after installing Java 21.

## Results

- Maven Enforcer accepted Java 21 and Maven 3.9.16.
- Three production Java sources compiled.
- Three test sources compiled.
- Six tests passed with zero failures, errors, or skips.
- Testcontainers connected to Docker and started PostgreSQL 18.4.
- Flyway validated and applied one migration to an empty database.
- The `app` and `audit` schemas and `public.flyway_schema_history` table were verified.
- Both ArchUnit foundation rules passed.
- The executable Spring Boot JAR was created.

Runtime smoke evidence against the Compose PostgreSQL service:

| Check | Result |
|---|---|
| PostgreSQL container health | `healthy` |
| `GET /api/v1/health/public` | `200` |
| Public response | `{"status":"UP","service":"invoicenow-workbench-api"}` |
| Unrecognized protected API route | `401` |

## Defects caught during verification

### Invalid Spring Boot parent coordinate

Initializr metadata supplied `4.1.0.RELEASE`, while Maven Central publishes the stable parent as `4.1.0`. The first clean build failed before compilation. The POM was corrected to the published coordinate and the build then compiled normally.

### Separate management-port assumption

The initial configuration forced a separate management server during a random-port test. The contract test received `401` when requesting Actuator health through the application port. The unnecessary management-port override was removed, producing the intended single-port MVP and a passing bounded health test.

### Generated development password

Spring Security initially created and logged a generated development password. A no-user `UserDetailsService` now makes the security boundary explicit until the dedicated identity phase. Public health remains available, while no protected application route has a usable default identity.

## Known warning

The current Spring test stack emits a Mockito dynamic-agent warning on Java 21 even though this foundation does not define mocks. It does not affect the six passing tests. Track it during dependency/JDK upgrades rather than hiding it with an undocumented JVM flag.

## Remaining verification

- Run the same Maven `verify` goal in GitHub Actions.
- Link the successful workflow run to issues `IRW-005` through `IRW-007`.
- Re-run from a clean checkout after merge.
