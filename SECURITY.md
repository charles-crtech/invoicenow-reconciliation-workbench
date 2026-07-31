# Security Policy

## Supported versions

This project is pre-release. Only the latest tagged release will be supported once releases begin.

## Reporting a vulnerability

Do not disclose suspected vulnerabilities in a public issue. Use GitHub's private vulnerability reporting feature when it is enabled for the repository. If that feature is unavailable, contact the repository owner through the private contact method shown on the owner's GitHub profile.

Include:

- affected version or commit;
- reproduction steps;
- expected and observed behaviour;
- potential impact; and
- any suggested mitigation.

Do not include real credentials, personal information, or third-party data in a report.

## Project security boundaries

- All portfolio transaction data must be synthetic.
- The application will not connect to IRAS or the live InvoiceNow network.
- Uploaded documents and text are untrusted.
- Authorization is enforced in the backend.
- Secrets remain outside source control and frontend bundles.
- AI assistance, if enabled, uses read-only tools and cannot mutate business state.

## Development safeguards

- Dependency and container checks will run in CI.
- PostgreSQL integration tests will cover constraints and authorization-sensitive behaviour.
- XML parsing will disable external entities and unsafe network retrieval.
- Logs will avoid tokens and full raw invoice payloads.
- Known security limitations will be recorded before a release is tagged.
