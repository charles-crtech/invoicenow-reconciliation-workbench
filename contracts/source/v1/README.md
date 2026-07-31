# Synthetic Source Contract v1

Contract version: `1.0`

This directory is the source of truth for the synthetic dataset consumed by the
InvoiceNow Reconciliation Workbench. It defines equivalent JSON and CSV
serializations for suppliers, invoices with owned lines, and ledger entries.

All fixture identifiers and transactions are fictional. This contract must never
be used to imply InvoiceNow accreditation, connection to IRAS, or tax advice.

## Artifact map

| Artifact | Role |
|---|---|
| `schemas/dataset.schema.json` | JSON Schema Draft 2020-12 contract for one JSON dataset |
| `schemas/csv-bundle.schema.json` | JSON Schema for the machine-readable CSV metadata document |
| `csv-contract.json` | Exact CSV file names, column order, primitive types, and format rules |
| `field-dictionary.md` | Meaning, requirement, bounds, and ownership of every field |
| `normalization-rules.md` | Original-value, normalization, cross-record, and failure rules |
| `fixtures/valid/` | Equivalent, structurally valid JSON and three-file CSV bundle |
| `fixtures/invalid/cases.json` | Invalid fixture index with stable expected reason codes |

## Two serializations, one canonical model

JSON uses one envelope with `suppliers`, `invoices`, and `ledger_entries` arrays.
Invoice lines are nested under their owning invoice.

CSV uses a three-file bundle:

1. `suppliers.csv` contains one row per supplier;
2. `invoices.csv` contains one row per invoice line and repeats immutable invoice
   header fields; and
3. `ledger_entries.csv` contains one row per ledger entry.

Every source artifact identifies `contract_version` `1.0` and the same lowercase
`dataset_id`. CSV headers and order are normative. JSON property order is not.

## Compatibility policy

- Patch documentation can clarify wording without changing accepted data.
- A backward-compatible optional field requires a minor version and updated
  schemas, fixtures, and tests.
- Removing, renaming, reordering CSV columns, changing types/bounds/meaning, or
  making an optional field required needs a new major contract directory.
- Readers must reject an unsupported version; they must not silently guess.

## Format rules

- Encoding: UTF-8 without a byte-order mark.
- JSON media type: `application/json`; unknown properties are rejected.
- CSV: comma delimiter, double-quote quoting, doubled quote escapes, LF or CRLF
  record endings, and the exact headers in `csv-contract.json`.
- Dates: ISO 8601 calendar date `YYYY-MM-DD` with no time zone.
- Decimals: base-10 JSON numbers or CSV decimal text, no exponent notation in
  CSV, no `NaN`/infinity, maximum four fractional digits at the source boundary.
- Boolean CSV text: lowercase `true` or `false`.
- Nulls: JSON `null` and CSV empty fields are forbidden except for `item_code`.

The application uses `BigDecimal`; binary floating-point is not an authoritative
financial representation.

## Validation layers

1. Decode and enforce bounded syntax, version, file set, headers, property set,
   primitive types, patterns, and field counts.
2. Preserve the bounded original record before normalization.
3. Apply the documented normalization rules.
4. Enforce cross-field and cross-record invariants.
5. CSV import persists accepted aggregates and bounded quarantine evidence using
   the stable policy in `docs/domain/csv-import-and-quarantine.md`; JSON applies
   the equivalent boundary in IRW-202.

Schema-valid does not mean reconciled. Declared totals may disagree with line
sums, and posting dates may fall outside reporting periods; those are valid
control scenarios that must remain visible.

## Verification

From `backend/`:

```powershell
./mvnw --batch-mode --no-transfer-progress "-Dtest=SourceContractFixtureTest" test
./mvnw --batch-mode --no-transfer-progress verify
```

The tests parse every JSON metadata/schema/fixture file, verify exact CSV headers
and valid JSON/CSV equivalence, enforce cross-record references, and assert the
stable reason code for every indexed invalid fixture.

Local evidence: the focused contract suite passed 4 tests and the complete gate
passed 101 tests with no failures, errors, or skips, followed by executable JAR
packaging. Pull-request evidence: [GitHub Actions run 30619218433](https://github.com/charles-crtech/invoicenow-reconciliation-workbench/actions/runs/30619218433),
where the required `Java 21 verify` check passed in 37 seconds.
