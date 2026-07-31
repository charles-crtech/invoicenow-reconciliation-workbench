# Invoice Aggregate and Persistence

Status: Implemented and verified under `IRW-102`

## Purpose

The invoice module preserves synthetic source documents as auditable business
records while making discrepancies visible to later deterministic controls. It
establishes the invoice-to-supplier relationship, invoice-line ownership, source
identity, controlled corrections, terminal voiding, and the PostgreSQL boundary.

This module deliberately accepts inconsistent declared and calculated totals.
Those inconsistencies are evidence for the reconciliation workflow, not malformed
aggregate structure. Import parsing, tax-policy decisions, rule outcomes, and HTTP
CRUD remain later responsibilities.

## Identity and normalization

An invoice has a generated `InvoiceId` plus an immutable business identity:

- `SourceSystemCode`: 3 to 32 uppercase ASCII letters, digits, or `_`;
- `SourceRecordId`: 1 to 100 case-preserving supported characters;
- `SupplierId`: a required reference to an existing synthetic supplier;
- `DocumentType`: `INVOICE`, `CREDIT_NOTE`, or `DEBIT_NOTE`; and
- `DocumentNumber`: 1 to 64 uppercase supported characters.

The source system and record ID are globally unique as a pair. Document identity
is unique within source system, supplier, and document type. A document number is
therefore not treated as globally unique.

The immutable lowercase SHA-256 payload hash is provenance, not an authorization
token. Aggregate `toString()` output omits it and line details.

## Header and line invariants

Every invoice owns at least one line. Positive line numbers are unique and lines
are exposed in ascending order through an unmodifiable list. Descriptions and
optional item codes are bounded. Quantity is positive, has at most four decimal
places, and fits the database `NUMERIC(19,4)` range. Tax rates are represented as
four-place decimal fractions from zero through one.

Header declared net, tax, and gross totals and every line monetary value use one
ISO 4217 currency. Money retains the currency-specific minor-unit rule established
by IRW-100. The aggregate does not infer a tax rounding policy.

`issueDate <= postingDate` is structural. The reporting period is not required to
contain the posting date because a period mismatch is a seeded, detectable control
scenario.

Declared totals are retained exactly. The aggregate separately exposes line-sum
net, tax, and gross totals and `declared - calculated` differences. A non-zero
difference is valid domain state for later rule evaluation.

## Lifecycle

New invoices are `ACTIVE`. A correction returns a new immutable aggregate and
requires a 10-to-500-character reason plus a timestamp that does not move
backwards. It may replace dates, reporting period, declared totals, and lines.
Invoice ID, source identity, supplier, document identity, payload hash, and
creation time stay unchanged.

Voiding also requires a bounded reason and monotonic timestamp. `VOIDED` is
terminal: a voided invoice cannot be corrected or voided again. PostgreSQL repeats
the active/void-reason state rule.

## Persistence boundary and Flyway V3

The public `InvoiceRepository` application port uses domain types only. The JPA
adapter, persistence entities, embedded line key, and Spring Data repositories are
package-private. Invoice and line rows are mapped separately so the domain model
does not acquire JPA collection semantics.

`V3__create_invoices_and_lines.sql` adds:

- `app.invoices`, with supplier foreign key, both identity constraints, normalized
  field checks, declared values, lifecycle reasons, timestamps, and optimistic
  version;
- `app.invoice_lines`, with `(invoice_id, line_number)` primary key and bounded
  structural checks; and
- supplier and reporting-period indexes for later workflow queries.

Invoice deletion is not exposed. Both foreign keys use `ON DELETE RESTRICT` to
avoid silently destroying financial evidence. On a controlled aggregate update,
the adapter replaces the owned line set inside the same transaction. Hibernate
assigns version `0` on insert and increments it on update.

## Verification

Executed with the committed Maven Wrapper under Java 21:

```powershell
./mvnw --batch-mode --no-transfer-progress "-Dtest=InvoiceTest,ArchitectureRulesTest" test
./mvnw --batch-mode --no-transfer-progress "-Dtest=InvoiceRepositoryIntegrationTest" test
./mvnw --batch-mode --no-transfer-progress verify
```

Local evidence:

- invoice domain suite: 15 tests passed;
- PostgreSQL invoice repository/migration suite: 10 tests passed;
- five production architecture rules passed;
- clean PostgreSQL 18.4 migration reached Flyway V3; and
- complete gate: 77 tests passed with no failures, errors, or skips, followed by
  executable JAR packaging.

Pull-request evidence: [GitHub Actions run 30617581884](https://github.com/charles-crtech/invoicenow-reconciliation-workbench/actions/runs/30617581884),
where the required `Java 21 verify` check passed in 36 seconds.

## Explicit boundaries

IRW-102 does not implement CSV/JSON parsing, original-versus-normalized import
storage, control-rule outcomes, final tax calculation policy, audit events,
authorization, HTTP resources, or hard deletion. These remain separate issues so
their contracts and evidence are independently reviewable.
