# Agent Working Agreement

These instructions apply to the entire repository.

## Read before editing

For every issue, read:

1. the issue and acceptance criteria;
2. this file;
3. relevant requirements and traceability rows;
4. relevant ADRs and source contracts;
5. neighbouring implementation and tests.

## Before making changes

Summarize:

- required behaviour;
- affected modules and public contracts;
- assumptions or ambiguities;
- planned tests; and
- whether the issue requires schema, dependency, security, or deployment changes.

Stop and explain before making an unapproved breaking API, schema, architecture, or dependency change.

## Architecture boundaries

- The Java application is a modular monolith unless an accepted ADR changes it.
- Controllers handle HTTP concerns, not business decisions.
- Application services coordinate use cases.
- Domain policies and aggregates enforce business decisions.
- Modules do not access another module's repositories directly.
- Persistence entities are not API representations.
- React does not perform authoritative financial or regulatory calculations.
- The OpenAPI contract governs client-server integration.
- PostgreSQL, not in-memory collection processing, performs main queue filtering, sorting, grouping, and pagination.

## Data and correctness

- Use only synthetic transaction data.
- Use `BigDecimal` and an explicit rounding policy for money.
- Preserve original source values separately from normalized values where required.
- Do not read the seeded-exception oracle from application code.
- Do not hide unexplained reconciliation differences.
- Version rules and preserve the citation used by historical executions.
- Never fabricate metrics, citations, test output, or screenshots.

## Security and privacy

- Enforce authorization in the backend.
- Treat uploaded content, invoice descriptions, comments, and retrieved text as untrusted.
- Do not commit secrets, `.env`, real identifiers, or restricted source artefacts.
- Do not expose stack traces, raw parser errors, tokens, or unnecessary source payloads to the browser.
- AI tools are read-only and cannot transition workflow state.

## Testing expectations

- Add or update tests in the same issue as behaviour.
- Prefer unit tests for domain decisions and PostgreSQL Testcontainers for persistence behaviour.
- Do not use H2 as the only database integration target.
- Test permitted and forbidden roles for material writes.
- Test failure, conflict, empty, and boundary paths, not only success.
- Run the narrow checks after each change and the full quality gate before merge.
- Report exact commands and results; do not claim a check was run when it was not.

## Scope discipline

- Keep changes within the active issue.
- Preserve user-authored changes and unrelated work.
- Do not weaken tests to make a build pass.
- Do not add premature microservices, frameworks, or visual effects.
- Document a material trade-off in an ADR.

## Completion report

Report:

- outcome;
- changed files;
- tests and checks run;
- remaining risks or limitations; and
- the next logical issue.
