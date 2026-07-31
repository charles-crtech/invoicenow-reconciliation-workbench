# Core Domain Value Objects

Status: Implemented under `IRW-100`; local verification passed, pull-request CI pending

## Purpose

The first domain types make invalid money, date-range, and aggregate-identity
states harder to represent before supplier, invoice, and ledger persistence is
introduced. They contain no Spring, JPA, HTTP, or serialization concerns.

## Money

`Money` pairs `BigDecimal` with `java.util.Currency` and normalizes values to the
currency's ISO minor-unit scale. Construction uses `RoundingMode.UNNECESSARY`,
so an input such as SGD `12.345` is rejected rather than silently changed. An
input such as `12.0000` is accepted because scale normalization loses no value.

Addition, subtraction, and comparison require matching currencies. Multiplication
and explicit rounded construction require a caller-provided `RoundingMode`.
The project has deliberately not selected a global tax-rounding policy in this
issue; the later versioned control/scenario policy must make that decision.

Negative money is valid because credit notes, reversals, differences, and ledger
entries need signed amounts. Aggregates will enforce any context-specific
non-negative invariant.

## Reporting periods

`ReportingPeriod` uses a half-open interval:

```text
[startInclusive, endExclusive)
```

This eliminates double membership at adjacent boundaries. The object accepts
arbitrary non-empty date ranges and provides a calendar-month factory, so later
scenarios can use monthly, quarterly, or custom periods without redefining the
primitive. Adjacency and overlap are separate operations.

## Identifiers

Supplier, invoice, and ledger-entry IDs are distinct record types backed by
UUIDs. Each type provides random generation and strict parsing. Parsing accepts
canonical hyphenated UUID text case-insensitively, rejects surrounding
whitespace and shortened forms, and renders lowercase canonical text.

The common `UuidIdentifier` contract enables infrastructure utilities without
making identifiers interchangeable. Source-system IDs, supplier codes, document
numbers, and registration-shaped fields are excluded until their versioned
source and aggregate rules are defined.

## Explicit boundaries

This issue does not add database columns, JPA mappings, JSON formats, public API
contracts, source-field rules, or a tax-rounding policy. Those decisions belong
to the aggregate, migration, source-contract, and control-rule issues that use
these primitives.

## Verification

Executed with the committed Maven Wrapper under Java 21:

```powershell
./mvnw --batch-mode --no-transfer-progress "-Dtest=MoneyTest,ReportingPeriodTest,DomainIdentifierTest" test
./mvnw --batch-mode --no-transfer-progress "-Dtest=ArchitectureRulesTest" test
./mvnw --batch-mode --no-transfer-progress verify
```

- focused value-object suites: 21 tests passed;
- production architecture rules: 2 tests passed; and
- complete repository gate: 27 tests passed, with PostgreSQL 18.4/Flyway
  migration integration and executable JAR packaging included.

The host currently lacks a Java installation, so these wrapper commands were
executed inside a Java 21 container. The full gate mounted the Docker socket for
Testcontainers. GitHub Actions independently repeats the wrapper `verify` goal.
