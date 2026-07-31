# Contributing

## Workflow

1. Select one approved `IRW-*` issue.
2. Create a short-lived branch from `main`.
3. Read `AGENTS.md`, relevant requirements, ADRs, and source contracts.
4. Implement the smallest complete change, including tests and documentation.
5. Run the narrow checks and the repository quality gate.
6. Open a pull request using the template.
7. Merge only after required checks pass and review findings are resolved.

## Branch and commit conventions

Suggested branch names:

- `foundation/irw-002-source-register`
- `feature/irw-203-job-progress`
- `fix/irw-306-conflict-response`
- `docs/irw-800-case-study`

Commit subjects should be imperative, scoped, and connected to the issue, for example:

```text
docs: establish InvoiceNow source register (IRW-002)
feat(imports): enforce batch idempotency (IRW-200)
test(exceptions): cover stale transition conflict (IRW-306)
```

## Pull-request expectations

- Explain the business behaviour, not only file changes.
- Link the issue and requirement IDs.
- Identify schema, API, security, dependency, and deployment effects.
- Include exact test commands and results.
- Include screenshots only for a relevant UI change.
- Never include credentials, real invoice data, or restricted source documents.

## Definition of review-ready

- Acceptance criteria are satisfied.
- New behaviour has appropriate automated tests.
- Documentation and traceability are current.
- Static analysis and builds pass.
- Known limitations are explicit.
- The diff contains no unrelated generated or local files.

## Responsible disclosure

Do not open a public issue for a suspected vulnerability. Follow [SECURITY.md](SECURITY.md).
