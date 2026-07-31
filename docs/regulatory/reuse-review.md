# Source Reuse Review

Status: Foundation baseline

Issue: `IRW-002`

Review date: 31 July 2026

## Decision

The repository will use a link-and-citation model for official regulatory and technical materials. It will not redistribute official PDFs, schemas, Schematron bundles, example files, screenshots, or substantial excerpts until the terms for the exact artefact have been reviewed and recorded.

## Source-specific treatment

### IRAS e-Tax Guide

The second-edition PDF displays an all-rights-reserved notice and states that reproduction, transmission, or storage requires written permission. Therefore:

- do not commit the PDF;
- do not copy tables, annexes, or substantial text into this repository;
- link to the official PDF;
- paraphrase requirements in original language;
- record short section/page citations necessary for traceability; and
- use independently generated synthetic fixtures rather than copied annex examples.

### IRAS web page

The page may be linked and paraphrased for the educational analysis. Do not mirror the HTML, branding, images, or large passages. Record access and last-updated dates because the page can change.

### IMDA Technical Playbook and downloadable files

The landing page is approved as a discovery source. "Official Open" markings or public availability are not assumed to be a software/content licence. Review every TX document, schema, Schematron file, and sample independently before committing it. The default is:

- link to the official landing page;
- record exact artefact metadata;
- keep downloaded copies local and ignored; and
- write original tests and synthetic examples.

### OpenPeppol specifications

Public technical availability does not automatically authorize republishing a complete specification bundle. Record the terms attached to the exact BIS/schema release before redistribution. Prefer official URLs and retrieval documentation.

## Repository-authored content

Original code, synthetic fixtures, diagrams, tests, and documentation in this repository are MIT-licensed unless a file states otherwise. They must not incorporate restricted source text in a way that changes their licensing status.

## Citation rules

- Cite the publisher, title, version/date, section or page, URL, and access date.
- Clearly label project interpretations and assumptions.
- Do not imply endorsement by IRAS, IMDA, OpenPeppol, or EY.
- Do not use official logos or trade dress in the product interface.
- Keep quotations short and necessary; prefer paraphrase.
- Never present a project rule as legal advice.

## Open questions before XML implementation

Before `IRW-207`, complete an artefact-level review covering:

- the exact Singapore invoice syntax/profile selected;
- permitted use of schemas and Schematron files in a public repository;
- version and effective dates;
- transitive licence notices;
- whether official samples may be referenced, downloaded during setup, or redistributed; and
- the safest reproducible validation path when artefacts cannot be committed.
