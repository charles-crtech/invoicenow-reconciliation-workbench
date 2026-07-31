# System Context

Status: Approved foundation architecture

Issue: `IRW-004`

Baseline date: 31 July 2026

## Context diagram

```mermaid
flowchart LR
    analyst[Analyst]
    reviewer[Reviewer]
    admin[Administrator]
    uat[UAT / Project Lead]

    workbench[InvoiceNow Reconciliation Workbench]

    synthetic[Synthetic ERP, procurement, ledger, and XML-like files]
    sources[Public IRAS, IMDA, and OpenPeppol sources]
    idp[Demo identity mechanism]
    ai[Optional configured LLM provider]

    analyst -->|Imports, investigates, proposes resolutions| workbench
    reviewer -->|Reviews evidence and approves or reopens| workbench
    admin -->|Manages demo roles and active rule versions| workbench
    uat -->|Verifies requirements and release evidence| workbench

    synthetic -->|Versioned synthetic inputs| workbench
    sources -.->|Human-reviewed requirements and citations; no live runtime dependency| workbench
    idp <-->|Authentication claims| workbench
    ai <-->|Optional grounded draft requests; no write authority| workbench
```

## Boundary notes

- IRAS, IMDA, and OpenPeppol are information sources, not runtime integrations.
- The workbench does not connect to IRAS or the InvoiceNow network.
- All portfolio transaction data is synthetic.
- The identity mechanism will be selected in a later security ADR.
- The LLM provider is outside the MVP and remains disabled until the deterministic system and evaluation controls are complete.
- Human reviewers retain authority over workflow decisions.

## Trust boundaries

1. Browser to application API: authenticate, authorize, validate, rate-limit expensive operations, and return safe errors.
2. Uploaded file to import pipeline: treat every byte as untrusted and apply bounded parsing.
3. Application to PostgreSQL: use least privilege and migrations; the application identity cannot mutate audit history.
4. Application to optional LLM provider: minimize data, validate tool arguments, and send no secrets or unnecessary identifiers.
5. Public sources to rule design: human review and source versioning prevent mutable web content from changing runtime behaviour silently.
