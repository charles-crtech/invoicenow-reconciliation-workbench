package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import java.util.Objects;

public record ImportBatchRegistration(ImportBatch batch, boolean replayed) {

    public ImportBatchRegistration {
        Objects.requireNonNull(batch, "registered import batch must not be null");
    }
}
