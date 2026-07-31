# InvoiceNow Reconciliation Workbench

A Java full-stack exception-operations platform for synthetic Singapore GST and InvoiceNow reconciliation scenarios.

The workbench will ingest synthetic invoice, procurement, ledger, and Peppol-style records; apply versioned deterministic controls; reconcile source data to ledger and reporting-period totals; and give human reviewers a traceable workflow for investigating and resolving exceptions.

> This is an educational portfolio implementation using synthetic data and public IRAS, IMDA, and OpenPeppol materials. It is not an accredited InvoiceNow solution, does not connect to IRAS, and is not tax or legal advice.

## Current status

Foundation phase: product scope, governance, source register, requirements, architecture, and the modular-monolith decision are documented. Application code has not yet been scaffolded.

Tracked foundation work:

- `IRW-000`: product scope and target evidence
- `IRW-001`: repository governance
- `IRW-002`: regulatory source register and reuse review
- `IRW-003`: MVP requirements and traceability
- `IRW-004`: architecture diagrams and ADR-001

## Portfolio objective

This project complements the Responsible AI Complaint Triage project by supplying evidence in:

- Java 21 and object-oriented design;
- Spring Boot enterprise application development;
- REST APIs and OpenAPI contracts;
- PostgreSQL modelling and server-side querying;
- React and TypeScript client development;
- asynchronous jobs and Server-Sent Events;
- authentication, authorization, auditability, and concurrency control;
- testing, containerisation, CI/CD, and cloud deployment; and
- optional grounded agentic-AI orchestration after the deterministic core is complete.

See [job alignment](docs/evidence/job-alignment.md) for the evidence map.

## Planned architecture

```text
React + TypeScript client
          |
          | REST + Server-Sent Events
          v
Java 21 / Spring Boot modular monolith
          |
          +-- PostgreSQL
          +-- bounded temporary import storage
          +-- optional grounded AI workflow service
```

Architecture details:

- [System context](docs/architecture/system-context.md)
- [Container architecture](docs/architecture/container-architecture.md)
- [ADR-001: Use a modular monolith](docs/adr/ADR-001-modular-monolith.md)

## Documentation map

- [Product scope](docs/product-scope.md)
- [Regulatory source register](docs/regulatory/source-register.md)
- [Source reuse review](docs/regulatory/reuse-review.md)
- [MVP requirements](docs/requirements/mvp-requirements.md)
- [Requirements traceability matrix](docs/requirements/traceability-matrix.md)
- [Security policy](SECURITY.md)
- [Contribution workflow](CONTRIBUTING.md)

## Planned implementation stack

- Java 21 LTS
- Spring Boot on a supported Java 21-compatible release
- Maven Wrapper
- PostgreSQL and Flyway
- React and TypeScript
- OpenAPI-defined client-server contract
- JUnit 5, Testcontainers, Vitest, Testing Library, and Playwright
- Docker Compose and GitHub Actions

Dependency versions will be selected and pinned during `IRW-005`; this document does not promise unverified latest versions.

## Development principles

1. Deterministic controls remain authoritative for financial and regulatory calculations.
2. The browser never performs authoritative reconciliation logic.
3. Search, filtering, sorting, grouping, and pagination execute server-side.
4. Imported data is synthetic, versioned, reproducible, and traceable.
5. Unexplained differences remain visible.
6. Material workflow actions are authorized and audited.
7. AI may draft evidence-grounded review notes but cannot change records or close exceptions.
8. Public claims require reproducible evidence.

## Local setup

Application setup will be added in `IRW-005` through `IRW-007`. Until then, this repository contains the reviewed project foundation only.

## Licence

Repository-authored code and documentation are licensed under the [MIT License](LICENSE), except third-party materials and linked public sources, which retain their original terms. Regulatory documents are linked rather than redistributed unless reuse permission is established.
