# Requirements Traceability Matrix

Status: Foundation baseline; implementation evidence pending

Issue: `IRW-003`

Baseline date: 31 July 2026

## Source keys

- `SCOPE`: [Product scope](../product-scope.md)
- `IRAS-WEB`: `SRC-IRAS-WEB-001` in the [source register](../regulatory/source-register.md)
- `IRAS-GUIDE`: `SRC-IRAS-GUIDE-001` in the source register
- `ARCH`: [Container architecture](../architecture/container-architecture.md)
- `ADR-001`: [Modular monolith decision](../adr/ADR-001-modular-monolith.md)

The external sources justify the educational business context. The project requirements and acceptance criteria remain original engineering interpretations and must not be described as official certification requirements.

## Matrix

| Requirement | Business statement | Source basis | Planned components | Planned evidence | Status |
|---|---|---|---|---|---|
| `REQ-001` | Import synthetic source records once and retain diagnosable rejects | SCOPE; IRAS-GUIDE for invoice-data context | Import module, invoice module, PostgreSQL, batch UI | Import API tests, Testcontainers idempotency/quarantine tests, manifest reconciliation | In progress; CSV registration, bounded parsing, accepted persistence, quarantine, and 413-unit smoke reconciliation implemented; JSON/UI pending |
| `REQ-002` | Reconcile invoices, ledger, and period totals without hiding differences | SCOPE; IRAS-WEB/IRAS-GUIDE for reporting transaction context | Control module, reconciliation module, exception module | Rule unit tests, seeded scenario tests, reconciliation integration tests | Approved; not implemented |
| `REQ-003` | Prioritize work through server-side queue operations | SCOPE; target-role API/SQL evidence | Exception query API, PostgreSQL indexes, React queue | Combined-query integration tests, query plan, component/Playwright tests | Approved; not implemented |
| `REQ-004` | Inspect source, rule, calculation, and lineage before deciding | SCOPE; IRAS-WEB/IRAS-GUIDE citations | Invoice, controls, reconciliation, exception, audit, React detail | Detail contract tests, lineage integration test, UAT case | Approved; not implemented |
| `REQ-005` | Enforce review roles, valid transitions, and concurrency | SCOPE; enterprise control objective | Exception module, identity module, audit module | State-policy unit tests, authorization tests, stale-version integration test | Approved; not implemented |
| `REQ-006` | Observe long jobs live and recover after connection loss | SCOPE; target-role full-stack evidence | Import/reconciliation jobs, SSE adapter, React job state | SSE lifecycle tests, reconnect component test, Playwright interruption scenario | Approved; not implemented |
| `REQ-007` | Enforce permissions and preserve material-action evidence | SCOPE; enterprise security objective | Identity, exception, rules, audit, HTTP security | Role matrix tests, append-only database test, security review | Approved; not implemented |
| `REQ-008` | Reproduce the environment and evidence from a clean checkout | SCOPE; ARCH; ADR-001 | Build, Compose, Flyway, CI, smoke scripts | [Application foundation verification](../testing/application-foundation-verification.md), CI run, release smoke evidence | Foundation verified; release evidence pending |

## Evidence-state meanings

- `Approved; not implemented`: requirement and acceptance criteria are accepted, but no implementation claim is made.
- `In progress`: a tested requirement slice exists, but remaining acceptance behaviour is explicitly identified.
- `Implemented`: code exists and narrow automated tests pass.
- `Verified`: the full required quality gate passes and evidence links are recorded.
- `Released`: evidence belongs to a tagged release matching the deployed demo.

Update this matrix in the same pull request as a requirement, contract, test, or implementation-status change.
