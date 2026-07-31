# Initial Implementation Backlog

This backlog converts the approved product scope, requirements, architecture, and
project roadmap into bounded delivery issues. It is the repository's concise
status ledger; detailed acceptance criteria remain in the corresponding GitHub
issue and requirement documents.

Status values are `pending`, `in progress`, `review`, `complete`, `blocked`, or
`not applicable`.

## Delivery rules

- Work on one issue-scoped branch at a time and merge through a pull request.
- Add or update automated tests in the same issue as a behaviour change.
- Run narrow checks first and the full required quality gate before merge.
- Update this file when an issue changes state or its exit evidence changes.
- Treat optional anomaly and AI capabilities as gated extensions, not MVP work.
- Do not promote measurements or portfolio claims without reproducible evidence.

## Phase 0: repository, evidence, and architecture foundation

| ID | Issue | Status | Exit evidence |
|---|---|---|---|
| IRW-000 | Confirm product scope, target roles, and definition of done | complete | Approved product scope, non-goals, disclaimer, and target-role evidence map |
| IRW-001 | Create repository governance and `AGENTS.md` | complete | MIT licence, security policy, contribution workflow, issue templates, and protected `main` |
| IRW-002 | Create regulatory source register and reuse review | complete | Access-dated source register, claim boundaries, and artefact reuse decisions |
| IRW-003 | Write initial requirements traceability matrix | complete | Eight approved MVP requirements with acceptance criteria and traceability rows |
| IRW-004 | Record modular-monolith architecture decision | complete | Accepted ADR-001 plus system-context and container diagrams |

## Phase 1: application foundation

| ID | Issue | Status | Exit evidence |
|---|---|---|---|
| IRW-005 | Scaffold Spring Boot, Maven Wrapper, and quality checks | complete | Java 21 build, committed wrapper, executable JAR, Enforcer rules, and architecture checks |
| IRW-006 | Add PostgreSQL Compose service and Flyway baseline | complete | Healthy PostgreSQL service and empty-database migration evidence from Testcontainers |
| IRW-007 | Add public health endpoint, smoke test, and CI | complete | Public health contract, protected-route check, six passing tests, and required `Java 21 verify` CI |
| IRW-008 | Remove inapplicable Docker Dependabot updater | complete | Maven and GitHub Actions update sweeps pass without an unsupported root Docker scan |
| IRW-009 | Establish implementation backlog ledger | complete | `BACKLOG.md` covers every approved roadmap issue once and is linked from the README |

## Phase 2: domain and deterministic data

| ID | Issue | Status | Exit evidence |
|---|---|---|---|
| IRW-100 | Implement money, identifiers, and reporting-period value objects | complete | Unit and architecture tests prove currency, scale, rounding, typed identifier, equality, and period-boundary invariants |
| IRW-101 | Implement supplier aggregate and migrations | complete | Supplier domain tests plus PostgreSQL constraints and repository integration tests pass |
| IRW-102 | Implement invoice and invoice-line aggregates | complete | 77-test local gate and required Java 21 CI prove lifecycle, mismatch visibility, supplier linkage, V3 constraints, ordered line persistence, and optimistic versioning |
| IRW-103 | Implement ledger-entry model | complete | 97-test local gate and required Java 21 CI prove debit/credit exclusivity, signed values, observable cutoff mismatch, source uniqueness, V4 constraints, and persistence |
| IRW-104 | Define CSV and JSON source contracts | complete | JSON/CSV v1 schemas, field/normalization rules, equivalent valid fixtures, five stable invalid cases, 101-test local gate, and required Java 21 CI pass |
| IRW-105 | Build deterministic smoke-data generator | complete | Strict fixed-seed profile, byte-identical contract v1 JSON/CSV bundle, reconciled counts/totals, committed hash evidence, 109-test local gate, and required [Java 21 CI](https://github.com/charles-crtech/invoicenow-reconciliation-workbench/actions/runs/30621980323) pass |
| IRW-106 | Add scenario manifest and test-only oracle | complete | Public manifest and checksum-bound test oracle reconcile 413 logical records, source hashes, totals, clean coverage, and zero expected impact; oracle boundary/JAR exclusion, 113-test gate, and required [Java 21 CI](https://github.com/charles-crtech/invoicenow-reconciliation-workbench/actions/runs/30622890198) pass |
| IRW-107 | Build demo and performance dataset profiles | complete | Demo 10,000-invoice and maximum 100,000-invoice profiles reproduce bounded counts, totals, manifests, and hashes in the normal 117-test gate; ignored-output proof and required [Java 21 CI](https://github.com/charles-crtech/invoicenow-reconciliation-workbench/actions/runs/30623283414) pass without tracked large data |

Phase 2 exits only when core monetary and identity invariants are tested, the
generator is deterministic, and the same source contract can drive the later
import pipeline without hidden test-only data.

## Phase 3: import pipeline and deterministic controls

| ID | Issue | Status | Exit evidence |
|---|---|---|---|
| IRW-200 | Implement import-batch API and idempotency | complete | Atomic key/checksum replay and conflicts, protected APIs, V5 constraints, and exact-byte hash boundaries pass the 136-test local gate and required [Java 21 CI](https://github.com/charles-crtech/invoicenow-reconciliation-workbench/actions/runs/30625169287) |
| IRW-201 | Implement bounded CSV parser and quarantine | complete | Dependency-free streaming bounds, stable quarantine, transactional rollback, safe APIs, 413-unit smoke reconciliation, 158-test local gate, and required [Java 21 CI](https://github.com/charles-crtech/invoicenow-reconciliation-workbench/actions/runs/30627436695) pass |
| IRW-202 | Implement bounded JSON parser and quarantine | pending | Versioned JSON parsing enforces the same safety, lineage, and count invariants as CSV |
| IRW-203 | Add asynchronous job state and progress | pending | Durable job states, safe failures, restart behaviour, and status-resource tests pass |
| IRW-204 | Implement rule definitions and versioning | pending | Historical executions retain immutable rule versions and source citations |
| IRW-205 | Implement document identity and duplicate rules | pending | Pass, fail, not-applicable, duplicate, and boundary cases are covered |
| IRW-206 | Implement monetary and tax arithmetic rules | pending | Explicit decimal, tolerance, rounding, tax, and boundary cases are covered |
| IRW-207 | Implement secure XML parsing and validation | pending | Artefact reuse review is complete and XXE/entity-expansion/malformed fixtures fail safely |
| IRW-208 | Map control failures to exceptions | pending | Idempotent mapping preserves control evidence, materiality, rule version, and lineage |
| IRW-209 | Add seeded scenario mutation suite | pending | Each declared scenario is detected at a measured rate without application access to the oracle |

Phase 3 exits only when counts reconcile, re-import is safe, each MVP rule has
complete outcome-path tests, and malicious source fixtures fail without leaking
parser details.

## Phase 4: reconciliation and exception workflow

| ID | Issue | Status | Exit evidence |
|---|---|---|---|
| IRW-300 | Implement document-level reconciliation | pending | Source arithmetic and normalized totals reconcile or expose a traceable difference |
| IRW-301 | Implement source-to-ledger matching and ambiguity handling | pending | Exact, missing-side, mismatch, and ambiguous-match scenarios have deterministic results |
| IRW-302 | Implement period reconciliation | pending | Source, ledger, adjustment, reporting, and unexplained totals satisfy declared equations |
| IRW-303 | Implement transmission-population checks | pending | Expected, observed, duplicate, and missing transmission populations remain traceable |
| IRW-304 | Implement exception state machine | pending | All allowed and forbidden transitions, required reasons, and terminal/reopen paths are tested |
| IRW-305 | Implement assignment and comments | pending | Authorized assignment and sanitized comment operations retain actor and timestamp evidence |
| IRW-306 | Implement optimistic-lock conflict handling | pending | Concurrent stale writes return a stable conflict and do not create a successful audit event |
| IRW-307 | Implement append-only audit events | pending | Database permissions and integration tests prevent application update or deletion of audit rows |
| IRW-308 | Add dashboard summary and grouping APIs | pending | PostgreSQL executes bounded grouping with correct totals and stable public response contracts |

Phase 4 exits only when the clean scenario reconciles within tolerance, every
difference is explained or visibly unexplained, stale writes fail safely, and
material actions have correlated audit evidence.

## Phase 5: React and Lovable client

| ID | Issue | Status | Exit evidence |
|---|---|---|---|
| IRW-400 | Establish React/Lovable design system and routing | pending | Reviewed Lovable export or React shell has reusable tokens, accessible navigation, and route tests |
| IRW-401 | Generate or validate typed OpenAPI client | pending | Client generation is reproducible and contract drift fails CI |
| IRW-402 | Build dashboard with period selection | pending | Real aggregate API data drives responsive loading, empty, error, and selected-period states |
| IRW-403 | Build invoice queue with server-side query controls | pending | Search, filter, sort, and page state execute server-side and survive refresh in the URL |
| IRW-404 | Build invoice detail and lineage | pending | Source, normalized, import, control, and reconciliation evidence is accessible and consistent |
| IRW-405 | Build import queue and batch detail | pending | Batch status, counts, quarantine reasons, and safe failure states use the real API |
| IRW-406 | Build reconciliation list and detail | pending | Run versions, totals, item outcomes, and unexplained differences are visible and tested |
| IRW-407 | Build exception queue and URL state | pending | Combined filters, stable pagination, materiality sort, and shareable URLs pass component tests |
| IRW-408 | Build exception workspace and transitions | pending | Role-aware actions, reasons, comments, conflicts, and audit timeline pass component tests |
| IRW-409 | Add SSE import and reconciliation progress | pending | Reconnect, exactly-once completion handling, authorization, and polling fallback pass tests |
| IRW-410 | Prototype and evaluate optional invoice-network visual | pending | Keep only if aggregate API data adds investigative value and performance/accessibility evidence passes |

Phase 5 exits only when completed screens use the real API, critical states are
intentional, URL state is durable, and keyboard-critical workflows pass review.
Lovable may accelerate the interface, but exported code must live in this
repository and conform to the approved API, security, and accessibility rules.

## Phase 6: security, quality, delivery, and UAT

| ID | Issue | Status | Exit evidence |
|---|---|---|---|
| IRW-500 | Implement authentication and seeded demo identities | pending | Explicit development-profile identities authenticate without browser-visible or committed secrets |
| IRW-501 | Enforce role and resource authorization | pending | Analyst, reviewer, and administrator permit/forbid matrix passes at the API boundary |
| IRW-502 | Harden CORS, headers, validation, and error responses | pending | Allowlisted origins, upload bounds, security headers, and stable redacted problem responses are verified |
| IRW-503 | Add structured logging, metrics, and correlation IDs | pending | Requests, jobs, and audit events correlate without logging tokens or unnecessary source payloads |
| IRW-504 | Complete backend integration and security suite | pending | Full backend, PostgreSQL, architecture, authorization, failure, and security gates pass |
| IRW-505 | Complete frontend unit, accessibility, and Playwright suite | pending | Critical workflows pass unit, accessibility, conflict, reconnect, and end-to-end tests |
| IRW-506 | Add performance dataset tests and query-plan evidence | pending | Declared volumes meet measured latency goals with recorded PostgreSQL query plans |
| IRW-507 | Build complete CI pipeline and reproducible containers | pending | Required backend, frontend, security, container, and end-to-end PR checks pass from clean checkout |
| IRW-508 | Deploy the portfolio environment and smoke test | pending | Approved deployment uses controlled migrations and a repeatable reset/smoke path |
| IRW-509 | Complete UAT and release hardening | pending | Requirements-linked UAT, runbook, rollback notes, limitations, and release candidate are accepted |

Deployment provider, costs, identity design beyond seeded demo identities, and
public performance claims require explicit decisions before implementation or
publication.

## Phase 7: optional anomaly analytics

| ID | Issue | Status | Exit evidence |
|---|---|---|---|
| IRW-600 | Establish anomaly baseline and leakage review | pending | Approved split, leakage assessment, simple baseline, and decision metric are recorded |
| IRW-601 | Evaluate candidate anomaly scorer | pending | Candidate is compared across seed, period, and scenario against the simple baseline |
| IRW-602 | Integrate selected anomaly-review queue | pending | Only a justified scorer is versioned and presented as prioritization, never fraud/compliance judgment |

This phase starts only after the deterministic MVP is stable and only if the
experiment adds evidence beyond rule-driven materiality ranking.

## Phase 8: optional grounded AI workflow

| ID | Issue | Status | Exit evidence |
|---|---|---|---|
| IRW-700 | Decide Java-native versus Python AI orchestration | pending | Accepted ADR justifies process boundary, provider, model, cost, latency, and security trade-offs |
| IRW-701 | Build approved regulatory retrieval corpus | pending | Versioned, licensed or link-only sources have provenance, chunking, and retrieval tests |
| IRW-702 | Implement read-only evidence and rule tools | pending | Allowlisted tools validate arguments and cannot mutate application or workflow state |
| IRW-703 | Implement structured reviewer-assistance workflow | pending | Schema-valid brief distinguishes evidence, citations, calculations, and open questions |
| IRW-704 | Add grounding, injection, and abstention evaluation | pending | Fixed cases measure citation validity, support, policy adherence, injection resistance, and abstention |
| IRW-705 | Add reviewer disposition and AI audit evidence | pending | Human edits/disposition and prompt/model/tool versions are traceable without unsafe payload retention |

This phase requires a separate architecture decision and fixed evaluation plan.
The assistant remains read-only and can never set severity, alter financial
records, transition exceptions, or approve resolutions.

## Phase 9: portfolio packaging

| ID | Issue | Status | Exit evidence |
|---|---|---|---|
| IRW-800 | Write architecture and integration case study | pending | Case study links business workflow, design choices, trade-offs, tests, and limitations |
| IRW-801 | Publish measured performance and testing evidence | pending | Every published metric is reproducible from a tagged commit and declared environment |
| IRW-802 | Record three-minute demo and screenshots | pending | Media matches the tagged deployment and demonstrates real completed workflows |
| IRW-803 | Finalize resume bullets and interview evidence map | pending | Each claim links to repository evidence and separates completed from optional work |
| IRW-804 | Tag and verify the portfolio release | pending | Clean-checkout, deployed smoke, UAT, documentation, media, and tag SHA agree |

## Current delivery status

IRW-000 through IRW-009, IRW-100 through IRW-107, and IRW-200 through IRW-201
are complete. The versioned JSON/CSV contracts have deterministic smoke, demo,
and functional-scale profiles with public manifests and a checksum-bound oracle
isolated from production. Import registration, atomic idempotency, dependency-free
bounded CSV streaming, transactional accepted-record persistence, stable durable
quarantine, role-protected APIs, and safe problem responses pass the 158-test
local gate and required Java 21 CI. The smoke CSV batches persist 10 suppliers,
100 invoices, 203 lines, and 100 ledger entries as 413 accepted source units.
Phase 2 is complete, the CSV path of Phase 3 is established, and IRW-202 is the
next implementation issue.

The first meaningful vertical slice remains: generate a deterministic synthetic
CSV, import it through the API, persist accepted invoices, quarantine invalid
rows, and show the completed batch in a minimal React page. Domain invariants are
implemented first so this slice does not embed financial decisions in parsers,
controllers, or the browser.

## Portfolio claim boundary

No anomaly, AI, deployment, performance, UAT, or release claim is complete merely
because it appears in this backlog. Public claims require passing evidence from
the implemented issue and, where applicable, the tagged deployed release.
