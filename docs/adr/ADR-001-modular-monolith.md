# ADR-001: Use a Modular Monolith for the Java Backend

- Status: Accepted
- Date: 31 July 2026
- Issue: `IRW-004`
- Decision owners: Repository owner and project maintainer

## Context

The project must demonstrate credible Java/Spring enterprise development, REST APIs, PostgreSQL, integration testing, operational workflows, and optional AI integration. It is a portfolio application maintained by one developer and must remain understandable, testable, deployable, and inexpensive to operate.

The domain contains related transactional operations:

- import and quarantine;
- invoice and ledger persistence;
- deterministic controls;
- reconciliation;
- exception state transitions; and
- append-only audit evidence.

These operations benefit from strong consistency. Splitting them prematurely across services would introduce network failure, distributed tracing, cross-service authorization, contract coordination, and eventual-consistency problems without a demonstrated scaling or team-ownership need.

## Decision

Build the Java backend as one Spring Boot deployable organized into explicit business modules:

- identity and access;
- suppliers;
- invoices;
- imports and quarantine;
- controls;
- reconciliation;
- exception workflow;
- dashboard queries;
- audit; and
- optional assistant adapter.

Module rules:

- controllers do not contain domain decisions;
- modules expose explicit application services or published events;
- one module cannot access another module's repositories directly;
- persistence entities do not cross the public API boundary;
- architecture tests enforce selected package dependencies; and
- module-specific tests remain possible without exercising unrelated UI code.

The optional AI workflow may become a separate process only through a later ADR if Python/LangGraph framework requirements, model-provider isolation, independent scaling, or security controls justify the boundary.

## Consequences

### Positive

- One deployable is easier to build, test, secure, and demonstrate.
- PostgreSQL transactions can protect core consistency.
- A clean package structure still demonstrates enterprise design.
- Local and cloud environments require fewer moving parts.
- Refactoring a proven module boundary later is safer than predicting services now.

### Negative

- Poor discipline could allow modules to become tightly coupled.
- One deployment scales all modules together.
- A process-level failure can affect the complete backend.
- Future extraction requires deliberate contract and data-ownership work.

### Mitigations

- Add architecture tests during scaffolding.
- Keep module APIs explicit and repositories package-private where practical.
- Use module-oriented tests and documentation.
- Record cross-module events and dependencies.
- Measure performance before proposing extraction.

## Alternatives considered

### Microservice per business capability

Rejected for the MVP. It would demonstrate distributed infrastructure but add complexity unrelated to the current problem size and single-developer ownership.

### Traditional unstructured layered monolith

Rejected. A global controller/service/repository package structure would make ownership and future change harder to explain and enforce.

### Serverless functions per endpoint

Rejected. It would weaken the primary Spring Boot enterprise-application evidence and complicate consistent workflows and local reproducibility.

## Revisit triggers

Reconsider this decision only when evidence shows at least one of:

- a module requires materially different scaling or availability;
- a security boundary requires process isolation;
- a selected AI framework cannot reasonably run in the Java application;
- independent teams need separate release ownership; or
- measured deployment or runtime constraints cannot be resolved within the monolith.
