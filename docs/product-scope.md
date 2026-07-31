# Product Scope

Status: Approved foundation scope

Issue: `IRW-000`

Decision date: 31 July 2026

## Product statement

InvoiceNow Reconciliation Workbench is a portfolio-grade Java and React application for investigating synthetic invoice reconciliation exceptions in a fictional Singapore finance operation.

The product demonstrates enterprise application delivery through a domain in which correctness, traceability, workflow control, and human review matter. It is not intended to simulate a complete accounting package or IRAS submission product.

## Primary users

- Analyst: reviews invoices and exceptions, adds evidence, and proposes resolutions.
- Reviewer: approves or rejects proposed resolutions and may reopen exceptions.
- Administrator: manages demo identities, active rule versions, and scenario configuration.
- UAT/project lead: reviews requirement, test, and release evidence.

## Primary workflow

1. An authorized user imports a versioned synthetic dataset.
2. The application validates the source contract and quarantines invalid records.
3. Deterministic rules evaluate accepted records.
4. A reconciliation job compares document, ledger, and period totals.
5. The application creates traceable exceptions for control failures and unexplained differences.
6. An analyst searches and filters the exception queue, inspects evidence, and proposes a resolution.
7. A reviewer approves, rejects, or reopens the resolution.
8. Every material action appears in an immutable audit timeline.

## MVP boundary

The MVP includes:

- Java 21 and Spring Boot API;
- PostgreSQL schema and Flyway migrations;
- deterministic synthetic smoke dataset;
- CSV import with idempotency and quarantine;
- initial versioned control rules;
- document and source-to-ledger reconciliation;
- exception queue and evidence detail;
- assignment, comments, and controlled state transitions;
- server-side search, filter, sort, and pagination;
- React and TypeScript operational client;
- Server-Sent Events for import and reconciliation progress;
- seeded demo identities with role enforcement;
- append-only audit events;
- automated backend, database, frontend, and end-to-end tests; and
- local Docker Compose startup.

## Target portfolio release

The portfolio release adds deployment, production-oriented hardening, grouping and dashboard aggregates, measured performance, full UAT evidence, and an optional grounded AI reviewer-assistance workflow.

## Explicit non-goals

The project will not:

- connect to IRAS or the live InvoiceNow network;
- claim accreditation or certification;
- use real company, supplier, invoice, UEN, or taxpayer data;
- provide tax or legal advice;
- determine fraud or compliance automatically;
- allow an anomaly model or LLM to close exceptions;
- implement a general ledger or full accounting system;
- split ordinary CRUD resources into separate microservices;
- make Power BI the primary interface; or
- prioritize visual effects over correctness and accessibility.

## Success evidence

The project is portfolio-ready only when:

- a clean checkout has a documented startup path;
- deterministic scenario generation is reproducible;
- import counts and reconciliation totals balance or expose differences;
- server-side queue operations are proven by integration tests;
- workflow authorization and optimistic-lock conflicts are tested;
- the deployed demo matches a tagged release;
- performance and quality claims are measured; and
- completed and future capabilities are labelled honestly.

## Public disclaimer

> This is an educational portfolio implementation using synthetic data and public IRAS, IMDA, and OpenPeppol materials. It is not an accredited InvoiceNow solution, does not connect to IRAS, and is not tax or legal advice.
