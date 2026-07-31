# MVP Requirements

Status: Approved for planning; not implemented

Issue: `IRW-003`

Baseline date: 31 July 2026

## Requirement conventions

- `MUST` identifies MVP acceptance behaviour.
- Regulatory references support the fictional scenario; they do not certify compliance.
- Business rules remain versioned and traceable to the source register.
- Acceptance evidence will link to tests, API examples, and tagged-release artefacts as implementation proceeds.

## REQ-001: Idempotent synthetic invoice import

As an analyst, I need to import a declared synthetic CSV dataset so that accepted records enter the workbench once and invalid records remain diagnosable.

The system MUST:

- authorize the import request;
- validate declared source type, file size, and content type;
- calculate a source checksum and enforce an idempotency policy;
- parse according to a versioned source contract;
- persist accepted records using fixed-precision monetary types;
- quarantine rejected records with safe reason codes;
- expose accepted, rejected, and quarantined counts; and
- retain a dataset manifest and import-batch identity.

Acceptance criteria:

```gherkin
Given a versioned smoke CSV containing valid and deliberately invalid synthetic rows
When an authorized analyst imports the file
Then valid rows are persisted exactly once
And invalid rows are quarantined with stable reason codes
And accepted plus rejected plus quarantined counts reconcile to the manifest
And importing the same checksum again does not duplicate invoices
```

## REQ-002: Deterministic reconciliation run

As an analyst, I need to reconcile accepted invoices against synthetic ledger records and reporting-period totals so that explained and unexplained differences are visible.

The system MUST:

- execute a versioned deterministic rule set;
- calculate document-level arithmetic results;
- classify source-to-ledger matches, mismatches, ambiguity, and missing sides;
- aggregate source, ledger, explained-adjustment, and reporting totals;
- persist the tolerance and rule-set version;
- create traceable exceptions for configured failures; and
- never force an unexplained difference into an unexplained "other" adjustment.

Acceptance criteria:

```gherkin
Given a completed import with one seeded amount mismatch
When an authorized user starts reconciliation for the reporting period
Then the run completes with the declared rule-set version
And the mismatch appears in the item results and exception queue
And the unexplained difference equals the seeded reconciliation impact
And the clean control scenario reconciles within the declared tolerance
```

## REQ-003: Server-side exception queue

As an analyst, I need to search, filter, sort, and page through exceptions so that I can focus on the material work relevant to me.

The system MUST:

- support a bounded free-text query over approved fields;
- filter by status, severity, rule code, assignee, supplier, reporting period, and materiality range;
- sort by allowlisted public fields;
- return stable pagination metadata;
- execute filtering, ordering, and pagination in PostgreSQL; and
- preserve the active query state in the frontend URL.

Acceptance criteria:

```gherkin
Given exceptions across multiple periods, severities, suppliers, and assignees
When an analyst applies combined filters and descending materiality sort
Then only matching rows are returned in stable order
And the response contains correct page metadata
And refreshing the browser preserves the query
And invalid sort fields and excessive page sizes are rejected safely
```

## REQ-004: Traceable exception evidence

As a reviewer, I need to see why an exception exists and where its values came from so that I can make a defensible decision.

The system MUST show:

- exception identity, state, severity, and materiality;
- observed and expected values;
- rule code, version, explanation, and source citation;
- related invoice, ledger entry, import batch, and reconciliation run;
- source and normalized value lineage where applicable;
- prior comments and transitions; and
- the correlated audit timeline.

Acceptance criteria:

```gherkin
Given a seeded source-to-ledger mismatch
When a reviewer opens its exception detail
Then the source and ledger values are visible
And the active rule version and citation are visible
And the import batch and reconciliation run are linked
And the displayed difference agrees with the persisted reconciliation result
```

## REQ-005: Controlled exception workflow

As an analyst and reviewer, I need role-appropriate assignment and resolution transitions so that exception decisions are reviewed and concurrent edits do not overwrite each other.

The system MUST:

- enforce the approved exception state machine;
- allow analysts to assign, comment, and propose resolution;
- reserve resolution approval for reviewers;
- require reasons for material transitions;
- enforce optimistic locking; and
- append an audit event only for a successful transition.

Acceptance criteria:

```gherkin
Given two reviewers opened the same exception version
When the first reviewer resolves it
And the second reviewer submits an update using the stale version
Then the second update receives a conflict response
And the resolved state is not overwritten
And no audit event records the failed stale update as successful
```

## REQ-006: Recoverable live job progress

As an analyst, I need live import and reconciliation progress so that I understand long-running work without repeatedly refreshing the page.

The system MUST:

- provide a normal job-status resource as the authority;
- publish bounded progress and terminal events through SSE;
- authorize event subscriptions;
- support client reconnection without duplicate completion actions;
- expose safe failure states; and
- keep the workflow usable through manual status refresh when SSE is unavailable.

Acceptance criteria:

```gherkin
Given an in-progress reconciliation job
When the browser's event connection is interrupted and restored
Then the client recovers the current status
And displays subsequent progress
And handles completion exactly once
And a failed live channel does not prevent manual status retrieval
```

## REQ-007: Role enforcement and auditability

As a project owner, I need server-side authorization and append-only material-action evidence so that the demo represents credible enterprise controls.

The system MUST:

- authenticate seeded demo identities;
- enforce analyst, reviewer, and administrator permissions in the API;
- deny unauthorized writes even when called outside the UI;
- record actor, action, aggregate, timestamp, request ID, and reason where required;
- prevent application identities from updating or deleting audit rows; and
- avoid secrets and unnecessary raw payloads in audit metadata.

Acceptance criteria:

```gherkin
Given an authenticated analyst
When the analyst attempts a reviewer-only approval through the API
Then the request is forbidden
And the exception state remains unchanged
And the attempt does not produce a successful business audit event
```

## REQ-008: Reproducible portfolio environment

As a recruiter or reviewer, I need a documented reproducible environment so that I can verify the application without relying on hidden local state.

The system MUST:

- build with committed Java and package-manager wrappers or lock files;
- start the declared local services through documented commands;
- apply Flyway migrations from an empty PostgreSQL database;
- generate or load a deterministic smoke dataset;
- expose public health status without leaking protected data;
- run the required automated quality gate in CI; and
- keep generated large data and secrets outside Git.

Acceptance criteria:

```gherkin
Given a clean checkout and the documented prerequisites
When a reviewer follows the setup and smoke-test instructions
Then the database migrates from empty
And the API and frontend report healthy
And the smoke dataset produces the documented scenario counts
And the same commit passes the pull-request quality workflow
```

## Deferred requirements

The following are outside the MVP and require separate acceptance criteria:

- anomaly ranking;
- AI-generated reviewer briefs;
- optional network visualisation;
- production identity-provider integration;
- Power BI reporting; and
- live InvoiceNow or IRAS connectivity, which remains prohibited for this portfolio.
