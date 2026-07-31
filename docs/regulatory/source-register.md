# Regulatory Source Register

Status: Foundation baseline

Issue: `IRW-002`

Baseline access date: 31 July 2026

## Purpose

This register identifies the authoritative public sources used to define the educational scenario. A source entry is not permission to redistribute its contents. Rules implemented later must cite a specific source version, section, and interpretation record.

## Registered sources

| ID | Publisher | Source | Version/date observed | Intended use | Repository treatment | Review state |
|---|---|---|---|---|---|---|
| `SRC-IRAS-WEB-001` | Inland Revenue Authority of Singapore | [GST InvoiceNow Requirement](https://www.iras.gov.sg/taxes/goods-services-tax-%28gst%29/gst-invoicenow-requirement) | Page last updated 25 June 2026 when accessed | Current scope, phased dates, transaction categories, exclusions, transmission due date | Link and paraphrase only; capture access metadata, not a mirrored page | Approved for reference |
| `SRC-IRAS-GUIDE-001` | Inland Revenue Authority of Singapore | [Adopting GST InvoiceNow Requirement for GST-Registered Businesses](https://www.iras.gov.sg/docs/default-source/e-tax/etaxguide_gst_invoicenow_requirement.pdf) | Second Edition, published 9 March 2026 | Detailed business requirements, definitions, scenarios, data elements, and validation context | Do not commit the PDF; link to it and record section/page citations | Approved for reference; redistribution restricted |
| `SRC-IMDA-PLAYBOOK-001` | Infocomm Media Development Authority | [InvoiceNow Technical Playbook](https://www.imda.gov.sg/how-we-can-help/nationwide-e-invoicing-framework/peppol-technical-playbook) | Page last updated 13 November 2025 when observed | Technical context, TX1/TX2/TX3 discovery, Singapore Peppol resources, validation references | Link to landing page; do not mirror downloadable bundles until each artefact is reviewed | Approved for discovery/reference |
| `SRC-OPENPEPPOL-TECH-001` | OpenPeppol | [Technical Documentation](https://peppol.org/documentation/technical-documentation/) | Continuously maintained documentation index; accessed 31 July 2026 | Discover applicable BIS, XML, validation, and eDelivery specifications | Pin the exact artefact/version before use; link rather than mirror by default | Approved for discovery/reference |
| `SRC-OPENPEPPOL-POST-001` | OpenPeppol | [Post Award Documentation](https://peppol.org/documentation/technical-documentation/post-award-documentation/) | Index observed 31 July 2026 | Identify current and upcoming billing specification releases | Record publishing and mandatory dates for any selected artefact | Approved for discovery/reference |

## Current regulatory facts used in the scenario

The following facts were observed in `SRC-IRAS-WEB-001` on 31 July 2026 and are context, not unversioned code constants:

- InvoiceNow is Singapore's nationwide e-invoicing network based on Peppol.
- GST-registered businesses are progressively required to transmit relevant invoice data through InvoiceNow-Ready Solutions.
- Published implementation phases include 1 November 2025, 1 April 2026, and further phases from 1 April 2028 through 1 April 2031.
- The current page lists standard-rated, zero-rated, and exempt supplies and specified purchase categories among mandatory transaction data.
- The page also lists excluded transaction categories and states a transmission due-date rule.

Before implementing a rule from these facts:

1. identify the exact source paragraph or table row;
2. record the source version/effective date;
3. distinguish legal requirement, guidance, example, and project policy;
4. write the interpretation in the requirement or rule definition;
5. add boundary and not-applicable tests; and
6. arrange human review for ambiguity.

## Version and checksum procedure

When an artefact may be used for test generation:

1. record its final resolved URL;
2. record title, publisher, edition/version, publication date, and access date;
3. calculate SHA-256 locally;
4. record licence/reuse terms and whether local storage is allowed;
5. store the checksum and retrieval instructions in this register or an artefact manifest;
6. keep the file out of Git unless redistribution is clearly permitted; and
7. make tests fail visibly when a required local artefact is absent rather than downloading mutable content silently.

## Change review

Recheck this register:

- before implementing or changing a regulatory rule;
- before a tagged portfolio release;
- before a job application that cites the project; and
- when a source page announces an amendment or new edition.

Changes to source interpretation require a new rule version and must not rewrite historical evidence.
