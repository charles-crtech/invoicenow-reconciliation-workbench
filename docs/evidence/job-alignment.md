# Job Alignment and Evidence Plan

Status: Baseline established 31 July 2026

Issue: `IRW-000`

## Target roles

1. [EY Junior AI Engineers (Associates/Senior Associates), Technology Consulting, AI & Data](https://careers.ey.com/ey/job/Junior-AI-Engineers-%28AssociatesSenior-Associates%29-Technology-Consulting%2C-AI-%26-Data-048583/1201028301/)
2. [EY Java Developer, Associate/Senior Associate, Technology Consulting](https://careers.ey.com/ey/job/Java-Developer%2C-AssociateSenior-Associate%2C-Technology-Consulting-048583/1391592533/)

Job descriptions change. This mapping records the postings observed on 31 July 2026 and must be rechecked before application or publication.

## Evidence map

| Role signal | Planned repository evidence | Phase | Status |
|---|---|---:|---|
| Core Java and object-oriented design | Domain value objects, aggregates, policies, architecture tests | 1-4 | Foundation architecture tests implemented; domain evidence planned |
| Spring Boot / Spring Framework | REST API, persistence, validation, security, health endpoints | 1-7 | Application and bounded health foundation implemented |
| REST APIs and integrations | Versioned OpenAPI, typed client, import and optional AI service contracts | 1-10 | Planned |
| SQL databases | PostgreSQL migrations, constraints, indexes, Testcontainers evidence | 1-8 | PostgreSQL/Flyway/Testcontainers baseline implemented |
| Enterprise application development | Requirements, ADRs, SIT/UAT, runbook, release evidence | 0-11 | In progress |
| Enhancement and troubleshooting | Safe failure modes, correlation IDs, operational runbook | 7-8 | Planned |
| CI/CD and Docker | Pull-request quality workflow and reproducible containers | 8 | First backend CI workflow verified on pull request |
| Cloud exposure | Tagged deployment, controlled migrations, smoke tests | 8 | Planned |
| Frontend exposure | React/TypeScript queues, detail screens, and live progress | 5-6 | Planned |
| NLP/ML/GenAI | Optional anomaly queue and grounded reviewer-assistance workflow | 9-10 | Planned |
| Agentic workflow | Distinct read-only evidence/retrieval/drafting responsibilities with evaluation | 10 | Planned |
| Documentation and communication | Source register, traceability, ADRs, case study, demo | All | In progress |

## Complement to Project 1

The Responsible AI Complaint Triage project is the stronger evidence for Python, NLP, model evaluation, uncertainty, and research communication. This project must prioritize Java/Spring, enterprise state, integrations, SQL, security, and delivery. Its AI extension should demonstrate grounded orchestration rather than repeat complaint classification.

## Evidence policy

- `Planned` is not a resume claim.
- An issue becomes `Implemented` only when code and automated tests exist.
- An issue becomes `Verified` only when the required quality gate and evidence link pass.
- Metrics remain placeholders until reproduced from a tagged release.
- Screenshots and demo videos must name the release they show.
