package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;

public record JsonImportResult(ImportBatch batch, boolean replayed) {}
