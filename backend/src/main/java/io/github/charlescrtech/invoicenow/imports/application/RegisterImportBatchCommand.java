package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.domain.IdempotencyKey;
import io.github.charlescrtech.invoicenow.imports.domain.ImportSourceType;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;

public record RegisterImportBatchCommand(
        String datasetId,
        String contractVersion,
        ImportSourceType sourceType,
        String sourceName,
        String contentType,
        long sourceSizeBytes,
        String sourceSha256,
        String manifestSha256,
        String idempotencyKey) {

    ImportBatchDraft toDraft() {
        return new ImportBatchDraft(
                datasetId,
                contractVersion,
                sourceType,
                sourceName,
                contentType,
                sourceSizeBytes,
                new Sha256Hash(sourceSha256),
                new Sha256Hash(manifestSha256),
                new IdempotencyKey(idempotencyKey));
    }

    record ImportBatchDraft(
            String datasetId,
            String contractVersion,
            ImportSourceType sourceType,
            String sourceName,
            String contentType,
            long sourceSizeBytes,
            Sha256Hash sourceSha256,
            Sha256Hash manifestSha256,
            IdempotencyKey idempotencyKey) {
    }
}
