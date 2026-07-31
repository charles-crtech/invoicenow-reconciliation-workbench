# Container Architecture

Status: Approved foundation architecture

Issue: `IRW-004`

Related decision: [ADR-001](../adr/ADR-001-modular-monolith.md)

## Container diagram

```mermaid
flowchart TB
    user[Web browser]

    subgraph public_app[Portfolio application]
        frontend[React + TypeScript client\nOperational UI and client state]
        backend[Java 21 Spring Boot modular monolith\nREST, SSE, use cases, controls, workflow]
        database[(PostgreSQL\nBusiness state, results, audit metadata)]
        temp[Bounded temporary import storage\nSynthetic uploads only]
    end

    subgraph optional_ai[Optional target-release AI boundary]
        orchestrator[Grounded workflow adapter/service]
        corpus[(Approved versioned source index)]
        provider[Configured LLM provider]
    end

    user -->|HTTPS| frontend
    frontend -->|JSON REST + SSE; OpenAPI contract| backend
    backend -->|JDBC/JPA; Flyway-managed schema| database
    backend -->|Stream/write/delete under retention policy| temp
    backend -.->|Read-only evidence request| orchestrator
    orchestrator -.->|Retrieve approved citations| corpus
    orchestrator -.->|Structured prompt and response| provider
```

## Spring Boot modules

```mermaid
flowchart LR
    http[HTTP adapters]
    identity[Identity and access]
    suppliers[Suppliers]
    invoices[Invoices]
    imports[Imports and quarantine]
    controls[Control rules]
    recon[Reconciliation]
    exceptions[Exception workflow]
    dashboard[Dashboard queries]
    audit[Audit]
    assistant[Optional assistant adapter]

    http --> identity
    http --> suppliers
    http --> invoices
    http --> imports
    http --> recon
    http --> exceptions
    http --> dashboard
    http -.-> assistant

    imports --> invoices
    imports --> controls
    controls --> exceptions
    recon --> invoices
    recon --> exceptions
    suppliers --> audit
    invoices --> audit
    imports --> audit
    recon --> audit
    exceptions --> audit
    assistant --> exceptions
```

Arrows represent approved application-service or event dependencies, not permission to access another module's repositories. Exact package dependencies will be enforced after scaffolding.

## Runtime responsibilities

### React client

- routes and accessible operational screens;
- typed OpenAPI client;
- URL-persisted queue state;
- loading, empty, error, conflict, and live-connection states;
- no authoritative financial calculations; and
- no secrets.

### Spring Boot application

- authentication and authorization;
- validation and safe API errors;
- import orchestration and idempotency;
- deterministic controls and reconciliation;
- exception workflow and optimistic locking;
- server-side query operations;
- SSE job events;
- audit-event creation; and
- optional read-only AI adapter.

### PostgreSQL

- transactional business state;
- constraints and relationship integrity;
- server-side filtering, sorting, grouping, and pagination;
- rule and execution versions;
- reconciliation and exception evidence; and
- append-only audit storage protected by database permissions.

### Temporary storage

- bounded synthetic upload lifecycle;
- checksum calculation input;
- no permanent public file serving; and
- documented cleanup and retention.

## Deployment principle

The core MVP has one frontend deployment, one Java application deployment, and one PostgreSQL database. The optional AI boundary is introduced only after an ADR shows that its framework, scaling, or isolation needs justify another process.
