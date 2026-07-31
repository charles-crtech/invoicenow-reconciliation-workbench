# Bounded CSV import and quarantine

Issue: `IRW-201`

The CSV adapter imports one previously registered source-contract v1 artifact:
`suppliers.csv`, `invoices.csv`, or `ledger_entries.csv`. The three batches share
the dataset and manifest checksum and are submitted in that dependency order.
This avoids an undocumented ZIP/container format while retaining exact artifact
checksums and transactional boundaries.

## Bounds and parsing

- file bytes: 1 through 256 MiB, also equal to the registered size;
- data records: at most 500,000 per artifact;
- logical record: at most 65,536 bytes before decoding;
- encoding: strict UTF-8 without a BOM;
- syntax: comma delimiter, exact header/order, double-quote escaping, LF or CRLF;
- fields: exact count plus the versioned field, type, normalization, and
  cross-field rules; and
- memory: the adapter reads 8 KiB chunks and emits one raw record at a time. It
  retains bounded typed values and quarantine candidates, not the raw file.

The artifact checksum covers every uploaded byte. A CSV record hash covers its
exact bytes before decoding and excludes only the terminating LF or CRLF. An
invoice reconstructed from several physical rows hashes the ordered,
length-prefixed record sequence, as specified in the normalization rules.

## Transactions and counts

Parsing and checksum verification finish before database writes. One PostgreSQL
transaction moves the batch through processing, persists accepted aggregates and
quarantine rows, and completes the batch. A database failure rolls that complete
unit back; a separate transaction then records a safe failed batch. Fatal file
syntax, encoding, or parser-limit failures likewise create no partial business or
quarantine records. A size, name, content-type, or checksum mismatch leaves the
registration available for the correct artifact.

Counts are mutually exclusive source units. Supplier and ledger rows each count
once. For invoice CSV, each reconstructed invoice header and each invoice-line
row count once, matching the manifest's `invoices + invoice_lines` fields. A
group-level invoice rejection stores a bounded header marker plus its line-row
quarantine evidence. Rejected count is reserved for file/transaction failures
that cannot safely retain record evidence; quarantined count is durable retained
evidence.

## Quarantine and security

Quarantine storage retains batch, source name, record number/type, exact-record
SHA-256, bounded original synthetic record, stable reason, optional allowlisted
field name, and timestamp. The read API returns only identifiers, hashes, reason
metadata, and time. It never returns the original record or parser/database
messages. Analyst role is required to import; analyst, reviewer, or administrator
may inspect the bounded metadata list. No usable identity is introduced before
IRW-500.

Completed upload replay returns the existing batch without reading the new part
or duplicating business records. Processing and failed batches do not silently
retry; an explicit reprocess policy remains future work.
