# Ledger-Entry Model and Persistence

Status: Implemented and verified under `IRW-103`

## Purpose

The reconciliation module now has an immutable synthetic ledger record to pair
with invoice evidence in later deterministic matching. The model captures source
identity, normalized matching references, accounting direction, tax magnitude,
posting time, reporting-period attribution, and provenance without implementing
a general ledger.

Ledger entries intentionally have no invoice foreign key. Later scenarios must be
able to represent exact matches, ambiguity, source-only invoices, and ledger-only
entries without manufacturing a relationship.

## Identity and normalized matching fields

Each entry has a generated `LedgerEntryId` and an immutable source identity:

- `SourceSystemCode` plus case-preserving `SourceRecordId`, unique as a pair;
- a lowercase SHA-256 source-payload hash;
- `AccountCode`, normalized to 3-to-32 uppercase supported characters;
- `CounterpartyReference`, normalized to 1-to-64 uppercase supported characters;
  and
- `LedgerDocumentReference`, normalized to 1-to-64 uppercase supported characters.

These references are matching inputs, not proof of a match. IRW-104 defines their
serialized contract. The import boundary will retain original source text before
normalization where required. The domain `toString()` omits source record identity
and the payload hash.

## Debit, credit, and tax semantics

Debit and credit are same-currency non-negative magnitude columns. Exactly one is
positive and the other is zero. This makes direction explicit and prevents
ambiguous zero-sided, both-sided, or negative-side rows.

The aggregate exposes:

- `side`: `DEBIT` or `CREDIT`;
- `signedAmount`: debit positive, credit negative; and
- `signedTaxAmount`: tax receives the same sign as the populated side.

Tax is a same-currency non-negative magnitude, but this issue does not assert that
tax must be less than or equal to the entry amount. That relationship depends on
account meaning and a later versioned control policy. All three values fit the
database `NUMERIC(19,4)` range; `Money` continues to enforce ISO currency scale
without silent rounding.

## Reporting period

The ledger model stores a posting date and the existing half-open
`[startInclusive, endExclusive)` reporting period. It exposes whether the posting
date belongs to that period but does not reject a mismatch. A cutoff mismatch is
a valid seeded reconciliation condition that must remain visible to controls.

## Immutability and persistence

There is no correction or delete operation. A reversal is represented as another
source entry, preserving the original evidence. New entries have no domain
persistence version; Hibernate assigns version `0` on insert. The repository can
look up by typed ID or source identity and returns domain types only.

The JPA adapter, entity, and Spring Data repository are package-private. An
architecture test keeps the reconciliation domain independent of its application
and infrastructure layers, Spring, and JPA.

`V4__create_ledger_entries.sql` adds `app.ledger_entries` with:

- UUID primary key and unique source identity;
- bounded normalized account, counterparty, and document references;
- explicit posting date and half-open reporting-period columns;
- same-row debit, credit, tax, currency, and provenance values;
- named checks for source/reference formats, debit-credit exclusivity,
  non-negative tax, currency, hash, reporting range, and version; and
- matching and reporting/account indexes for later reconciliation queries.

No table-level invoice relationship or cascade is added.

## Verification

Executed with the committed Maven Wrapper under Java 21:

```powershell
./mvnw --batch-mode --no-transfer-progress "-Dtest=LedgerEntryTest,ArchitectureRulesTest" test
./mvnw --batch-mode --no-transfer-progress "-Dtest=LedgerEntryRepositoryIntegrationTest" test
./mvnw --batch-mode --no-transfer-progress verify
```

Local evidence:

- ledger domain suite: 10 tests passed;
- PostgreSQL repository/migration suite: 9 tests passed;
- six production architecture rules passed;
- clean PostgreSQL 18.4 migration reached Flyway V4; and
- complete gate: 97 tests passed with no failures, errors, or skips, followed by
  executable JAR packaging.

Pull-request evidence: [GitHub Actions run 30618370218](https://github.com/charles-crtech/invoicenow-reconciliation-workbench/actions/runs/30618370218),
where the required `Java 21 verify` check passed in 34 seconds.

## Explicit boundaries

IRW-103 does not define CSV/JSON field serialization, original-value storage,
account semantics, source-to-ledger matching, ambiguity resolution, tolerance,
tax calculation or rounding, reporting totals, HTTP CRUD, authorization, audit
events, mutation, or hard deletion.
