# Source Contract v1 Field Dictionary

All fields are required unless marked optional. Bounds are Unicode code points
for display text and ASCII characters for patterned identifiers. `source_payload_hash`,
database UUIDs, persistence versions, statuses derived by lifecycle, and timestamps
are application-owned and are not source fields.

## Envelope and CSV correlation

| Field | Type / bound | Meaning |
|---|---|---|
| `contract_version` | exact string `1.0` | Selects this major/minor source contract |
| `dataset_id` | lowercase pattern `[a-z0-9][a-z0-9._-]{2,63}` | Correlates all artifacts in one synthetic dataset |

In JSON these occur once on the envelope. In CSV they are the first two fields of
every row so a file copied outside its bundle remains identifiable.

## Supplier fields

| Field | Type / bound | Meaning |
|---|---|---|
| `supplier_code` | 3–32, `[A-Z0-9][A-Z0-9_-]{2,31}` | Synthetic business key used by invoice references |
| `display_name` | nonblank text, max 200 | Untrusted fictional display text |
| `registration_identifier` | 9–64, `SYNTH-` prefix | Explicitly synthetic registration-shaped identifier |
| `gst_registered` | boolean | Fictional scenario flag; not regulatory evidence |
| `status` | `ACTIVE`, `INACTIVE`, `ARCHIVED` | Initial supplier lifecycle state |

## Invoice header fields

| Field | Type / bound | Meaning |
|---|---|---|
| `source_system` | 3–32, `[A-Z][A-Z0-9_]{2,31}` | Normalized originating system code |
| `source_record_id` | 1–100 supported, case-preserving | Source-local invoice identity; repeated on its CSV lines |
| `supplier_code` | supplier key | Must resolve to exactly one supplier in the dataset |
| `document_number` | 1–64 normalized supported | Supplier document reference, not globally unique |
| `document_type` | `INVOICE`, `CREDIT_NOTE`, `DEBIT_NOTE` | Document direction/type; does not select tax policy |
| `issue_date` | ISO date | Document issue date |
| `posting_date` | ISO date | Accounting posting date; must not precede issue date |
| `reporting_period_start` | ISO date | Inclusive reporting boundary |
| `reporting_period_end` | ISO date | Exclusive reporting boundary; must be after start |
| `currency` | ISO 4217 code, `[A-Z]{3}` | Currency shared by header and all owned lines |
| `declared_net` | decimal magnitude/sign, 19 digits / 4 scale | Source-declared net total |
| `declared_tax` | decimal magnitude/sign, 19 digits / 4 scale | Source-declared tax total |
| `declared_gross` | decimal magnitude/sign, 19 digits / 4 scale | Source-declared gross/payable total |

The source identity pair `(source_system, source_record_id)` is unique per invoice.
The tuple `(source_system, supplier_code, document_type, document_number)` is also
unique. Declared arithmetic inconsistencies remain structurally valid.

## Invoice-line fields

| Field | Type / bound | Meaning |
|---|---|---|
| `line_number` | positive integer | Stable, unique number within the invoice |
| `description` | nonblank text, max 500 | Untrusted fictional line description |
| `item_code` | optional; 1–64 supported | Synthetic item reference; the only nullable/blank field |
| `quantity` | positive decimal, max `999999999999999.9999` | Source quantity, maximum four fractional digits |
| `unit_price` | decimal, 19 digits / 4 scale | Source unit price in header currency |
| `net_amount` | decimal, 19 digits / 4 scale | Source line net in header currency |
| `tax_category` | `STANDARD_RATED`, `ZERO_RATED`, `EXEMPT`, `OUT_OF_SCOPE` | Scenario classification, not legal advice |
| `tax_rate` | decimal `0` through `1`, max four fractional digits | Fractional rate; `0.09` represents 9% |
| `tax_amount` | decimal, 19 digits / 4 scale | Source line tax in header currency |
| `gross_amount` | decimal, 19 digits / 4 scale | Source line gross in header currency |

Line arithmetic discrepancies remain structurally valid for deterministic
controls. Currency is explicit on a JSON line to make a mismatch observable; CSV
uses `line_currency` for the same purpose.

## Ledger-entry fields

| Field | Type / bound | Meaning |
|---|---|---|
| `source_system` | same as invoice | Originating ledger system |
| `source_record_id` | same as invoice | Unique source-local ledger row identity |
| `account_code` | 3–32, `[A-Z0-9][A-Z0-9._-]{2,31}` | Normalized synthetic account reference |
| `counterparty_reference` | 1–64 normalized supported | Matching input, normally a supplier code; no asserted relationship |
| `document_reference` | 1–64 normalized supported | Matching input, normally a document number |
| `posting_date` | ISO date | Ledger posting date |
| `reporting_period_start` | ISO date | Inclusive attributed period boundary |
| `reporting_period_end` | ISO date | Exclusive attributed period boundary |
| `currency` | ISO 4217 code | Shared by debit, credit, and tax |
| `debit_amount` | non-negative decimal | Positive only for a debit entry |
| `credit_amount` | non-negative decimal | Positive only for a credit entry |
| `tax_amount` | non-negative decimal | Tax magnitude; later policy supplies direction/account meaning |

Exactly one of debit or credit is positive; the other is zero. A posting date
outside the supplied reporting period is valid and observable.
