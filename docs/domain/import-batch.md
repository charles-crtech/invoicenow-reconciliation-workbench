# Import-batch registration and idempotency

Issue: `IRW-200`

An import batch is the durable identity and lifecycle record for one bounded
synthetic source. Registration deliberately precedes parsing: the CSV and JSON
upload adapters in `IRW-201` and `IRW-202` calculate source bytes, size, content
type, and SHA-256 before calling this application service.

## Identity and replay policy

The client supplies an `Idempotency-Key` of 8 to 128 allowlisted ASCII
characters. The server stores the first key but never returns it. A content
fingerprint consists of:

- dataset ID;
- source-contract version;
- source type (`CSV` or `JSON`);
- source SHA-256; and
- manifest SHA-256.

A first registration returns `201 Created`. The same key and registration
details return the original batch with `200 OK` and `Idempotent-Replay: true`.
The same content fingerprint under a different key also returns the original
batch. Reusing a key with changed details returns `409` and stable code
`IMPORT_IDEMPOTENCY_CONFLICT`.

PostgreSQL uniquely constrains both the idempotency key and content fingerprint.
The repository uses `INSERT ... ON CONFLICT DO NOTHING`, then resolves the
winning row, so concurrent requests cannot create duplicate batches. Import
registration does not create supplier, invoice, line, or ledger records.

`sourceSha256` is SHA-256 over the exact uploaded artifact bytes before UTF-8
decoding, line-ending handling, CSV unquoting, JSON parsing, or normalization.
`manifestSha256` is likewise over the exact uploaded manifest bytes. There is no
serialization canonicalization: byte changes intentionally produce a different
fingerprint even when parsed values might be equivalent. The adapters retain
the separately calculated per-logical-record hash required for accepted and
quarantined lineage.

## Bounds and safety

- only source contract `1.0` is accepted;
- source metadata is capped at 256 MiB;
- `CSV` requires `text/csv`; `JSON` requires `application/json`;
- source names must be bounded base names without path separators or controls;
- hashes are lowercase SHA-256; and
- counts, lifecycle timestamps, status, failure code, and version have matching
  domain and database constraints.

`POST /api/v1/import-batches` requires the analyst role. Read access permits the
analyst, reviewer, or administrator roles. No usable identity is shipped before
`IRW-500`; API tests use Spring Security test principals and CSRF tokens rather
than committing credentials.

## Current boundary

The endpoint registers metadata and an upload-adapter-produced checksum. It does
not accept raw bytes, parse files, or claim that `REQ-001` is complete. CSV
streaming/quarantine is `IRW-201`, JSON parsing/quarantine is `IRW-202`, and
durable asynchronous progress is `IRW-203`.
