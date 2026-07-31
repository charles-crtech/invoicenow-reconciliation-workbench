# Source Contract v1 Normalization and Integrity Rules

## Original versus normalized values

The importer must retain an approved bounded representation of each original
logical record before normalization. Original text is evidence and is never
silently overwritten. Normalized fields populate domain aggregates and matching
indexes. Browser/API output must continue to treat both as untrusted text.

`source_payload_hash` is deliberately absent from source artifacts. IRW-200,
IRW-201, and IRW-202 must define and test the stable byte/canonicalization scope
for each serialization, then compute SHA-256 from the captured original record.
The source cannot truthfully provide a self-hash without a circular contract.

IRW-200 defines the batch-level checksum as SHA-256 over the exact uploaded
artifact bytes before decoding or normalization; no line-ending, whitespace, or
JSON canonicalization occurs. IRW-201 defines a CSV logical-record hash over the
exact bytes from the first byte of a record through its final byte, excluding
only the terminating LF or CRLF. The hash is calculated before UTF-8 decoding,
CSV unquoting, or normalization. A reconstructed multi-line invoice hash uses
the file-order sequence of those record bytes, each prefixed by its unsigned
eight-byte big-endian length; this prevents ambiguous concatenation. IRW-202
defines JSON logical records as deterministic compact UTF-8 JSON. Known object
properties occur in contract order, unknown properties follow in lexical order,
array order is preserved, and values use their lossless parsed JSON-tree
representation. Supplier and ledger hashes cover their complete objects. An
invoice hash covers the complete invoice object including its ordered lines;
each line also receives its own canonical evidence hash. Insignificant source
whitespace and object-property order do not change these logical hashes, while
the batch checksum remains over the exact uploaded bytes. Each canonical record
is limited to 65,536 UTF-8 bytes. The full policy and parser bounds are in
`docs/domain/json-import-and-quarantine.md`.

## String normalization

1. Decode strict UTF-8; reject malformed byte sequences and a UTF-8 BOM.
2. Strip Unicode outer whitespace from all string fields.
3. Reject blank required fields after stripping.
4. Uppercase with locale-independent rules for supplier codes, source systems,
   document numbers, account codes, counterparty references, document references,
   item codes, currency, enum values, and CSV boolean text.
5. Preserve case for `source_record_id`, `display_name`, and `description` after
   outer stripping.
6. Do not collapse internal whitespace or apply Unicode compatibility
   normalization. Such a future change would alter identity/matching semantics.
7. Apply the field patterns and code-point bounds after normalization.

## Null and empty handling

- JSON unknown fields and explicit `null` are rejected except `item_code: null`.
- CSV requires exactly the declared field count. Empty fields are rejected except
  `item_code`.
- Empty `item_code`, JSON `null`, and omitted JSON `item_code` normalize to no item
  code. No other sentinel such as `NULL`, `N/A`, or `-` means null.

## Dates, booleans, integers, and decimals

- Dates parse strictly as `YYYY-MM-DD`; impossible dates are rejected.
- CSV booleans accept lowercase `true` and `false` only.
- `line_number` is base-10 digits without a sign and must be greater than zero.
- CSV decimals match `-?(0|[1-9][0-9]*)(\.[0-9]{1,4})?`; a leading plus,
  grouping separator, exponent, `NaN`, and infinity are rejected.
- JSON decimals must be finite base-10 numbers with at most four fractional
  digits when converted losslessly to `BigDecimal`.
- Currency minor-unit scale is checked after ISO currency resolution. No implicit
  rounding is allowed.

## Cross-field rules

- Invoice `posting_date` must not precede `issue_date`.
- Each reporting period is half-open and start must precede end.
- Invoice header and line currencies must match.
- An invoice owns at least one line with positive unique line numbers.
- In flattened CSV, every repeated header field for the same invoice source
  identity must be identical after normalization.
- Quantity is positive; tax rate is between zero and one inclusive.
- Exactly one ledger debit/credit amount is positive and the other is zero; ledger
  tax is non-negative; all three monetary fields use the ledger currency.

Declared document arithmetic and posting-period membership are not structural
rules. Mismatches remain accepted data for later deterministic controls.

## Cross-record rules

- All records and files in one ingestion unit use one contract version/dataset ID.
- Supplier codes and supplier registration identifiers are unique.
- Every invoice supplier code resolves to exactly one supplier record.
- Invoice source identity and document identity tuples are unique.
- Ledger source identity is unique.
- Counterparty/document references do not have to resolve: ledger-only and
  source-only scenarios are valid.

## Stable fixture reason codes

| Code | Contract failure represented in v1 fixtures |
|---|---|
| `CONTRACT_UNKNOWN_FIELD` | JSON contains a property outside the closed schema |
| `CONTRACT_CSV_HEADER` | CSV header names/order differ from metadata |
| `CONTRACT_SYNTHETIC_ID_REQUIRED` | Supplier identifier lacks the mandatory synthetic prefix |
| `CONTRACT_LEDGER_SIDE` | Both or neither debit/credit side is positive |
| `CONTRACT_CURRENCY_MISMATCH` | Invoice line currency differs from its header |

These codes identify contract-test evidence. Import/quarantine issues may map them
to a broader public error taxonomy but must not change the underlying reason.
