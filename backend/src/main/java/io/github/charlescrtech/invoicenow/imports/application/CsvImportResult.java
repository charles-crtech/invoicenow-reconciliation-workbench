package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;

public record CsvImportResult(ImportBatch batch, boolean replayed) {}
