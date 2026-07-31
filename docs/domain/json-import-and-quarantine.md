# Bounded JSON import and quarantine

Issue: `IRW-202`

The JSON adapter imports one previously registered source-contract v1
`dataset.json` envelope. The envelope contains suppliers, invoices with nested
lines, and ledger entries, so one database transaction can enforce references
without depending on upload order across files.

## Bounds and streaming

- file bytes: 1 through 256 MiB, also equal to the registered size;
- source units: at most 500,000 across suppliers, invoice headers, invoice
  lines, ledger entries, and retained top-level rejects;
- canonical logical record: at most 65,536 UTF-8 bytes;
- token stream: at most 20,000,000 tokens, nesting depth 8, property names 64
  characters, strings 4,096 characters, and number tokens 64 characters;
- encoding: strict UTF-8 without a BOM;
- syntax: exactly one JSON object, no trailing content or duplicate properties;
  and
- memory: Jackson streams the envelope and materializes one bounded supplier,
  invoice aggregate, or ledger record tree at a time. The adapter retains typed
  values and bounded quarantine candidates, never the raw file.

The file checksum covers every uploaded byte. It therefore changes with source
whitespace and property order. Logical-record hashes use deterministic compact
JSON: contract fields occur in contract order, unknown fields follow in lexical
order, arrays preserve source order, and values use Jackson's lossless JSON tree
representation. Supplier and ledger hashes cover their objects. An invoice hash
covers its complete object including ordered lines; each line also has its own
canonical evidence record. Insignificant whitespace and object-property order
therefore do not alter logical lineage, while the exact batch checksum still
detects any byte change.

## Validation, transactions, and counts

Closed properties, required and null rules, primitive types, normalization,
patterns, decimal bounds, cross-field rules, and duplicate identities are
validated against source contract v1. Supplier references and domain invariants
are enforced through the supplier, invoice, and ledger application services.
Jackson is already supplied by Spring Boot; this adapter adds no parser
dependency.

Parsing and checksum verification finish before database writes. One PostgreSQL
transaction starts the batch, persists all accepted aggregates and quarantine
rows, then completes it. A database failure rolls the entire unit back; a
separate transaction records a safe failed batch. Fatal syntax, encoding, or
parser-limit failures likewise leave no partial business or quarantine rows.
Name, media-type, size, or checksum mismatches leave the registration available
for the correct artifact.

Counts are mutually exclusive source units. Each supplier, invoice header,
invoice line, and ledger entry counts once. The functional smoke JSON therefore
reconciles as 10 suppliers + 100 invoices + 203 lines + 100 ledger entries = 413
accepted units, exactly like the three equivalent CSV imports. A rejected
invoice group retains one bounded invoice aggregate plus one record per nested
line, all with the same stable reason.

## Quarantine and security

Flyway V7 expands the constrained reason allowlist with
`CONTRACT_UNKNOWN_FIELD`; it does not change the quarantine shape. Durable
storage retains bounded original canonical evidence, but the read API exposes
only record identifiers, hashes, stable reason metadata, and timestamps. Raw
records and Jackson, database, or stack-trace details are never returned.

Only the analyst role may upload JSON. Analyst, reviewer, or administrator roles
may inspect safe quarantine metadata. Completed upload replay returns the prior
batch without reading the replacement part or duplicating records. Processing
and failed batches require a future explicit reprocess policy.
