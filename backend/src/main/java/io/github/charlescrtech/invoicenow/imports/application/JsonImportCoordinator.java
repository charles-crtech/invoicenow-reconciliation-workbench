package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchStatus;
import io.github.charlescrtech.invoicenow.imports.domain.ImportSourceType;
import io.github.charlescrtech.invoicenow.imports.infrastructure.json.BoundedJsonParser;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class JsonImportCoordinator {

    private final ImportBatchRepository batches;
    private final BoundedJsonParser parser;
    private final CsvImportTransactionService transaction;
    private final ImportBatchFailureService failures;

    JsonImportCoordinator(
            ImportBatchRepository batches,
            BoundedJsonParser parser,
            CsvImportTransactionService transaction,
            ImportBatchFailureService failures) {
        this.batches = batches;
        this.parser = parser;
        this.transaction = transaction;
        this.failures = failures;
    }

    public JsonImportResult importJson(
            ImportBatchId batchId,
            String fileName,
            String contentType,
            long size,
            InputStream input) {
        ImportBatch batch = batches.findById(batchId)
                .orElseThrow(ImportBatchNotFoundException::new);
        if (batch.status() == ImportBatchStatus.COMPLETED) {
            return new JsonImportResult(batch, true);
        }
        if (batch.status() != ImportBatchStatus.REGISTERED) {
            throw new JsonImportException(
                    "IMPORT_BATCH_STATE_CONFLICT", false, "import batch cannot be imported in its current state");
        }
        validateMetadata(batch, fileName, contentType, size);

        JsonImportPlan plan;
        try {
            plan = parser.parse(Objects.requireNonNull(input, "JSON input must not be null"), batch);
        } catch (JsonImportException exception) {
            if (exception.terminal()) {
                failures.failRegistered(batchId, exception.code(), 1);
            }
            throw exception;
        }
        if (!plan.sourceSha256().equals(batch.sourceSha256())) {
            throw new JsonImportException(
                    "IMPORT_SOURCE_CHECKSUM_MISMATCH", false, "uploaded JSON checksum differs from registration");
        }
        try {
            return new JsonImportResult(transaction.commitJson(batchId, plan), false);
        } catch (JsonImportException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            failures.failRegistered(batchId, "IMPORT_TRANSACTION_FAILED", plan.sourceUnitCount());
            throw new JsonImportException(
                    "IMPORT_TRANSACTION_FAILED", false, "JSON import transaction failed");
        }
    }

    private static void validateMetadata(
            ImportBatch batch,
            String fileName,
            String contentType,
            long size) {
        if (batch.sourceType() != ImportSourceType.JSON
                || !"dataset.json".equals(fileName)
                || !batch.sourceName().equals(fileName)
                || !batch.contentType().equals(contentType)
                || batch.sourceSizeBytes() != size) {
            throw new JsonImportException(
                    "IMPORT_SOURCE_METADATA_MISMATCH", false, "uploaded JSON metadata differs from registration");
        }
    }
}
