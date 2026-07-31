package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchStatus;
import io.github.charlescrtech.invoicenow.imports.domain.ImportSourceType;
import io.github.charlescrtech.invoicenow.imports.infrastructure.csv.BoundedCsvParser;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class CsvImportCoordinator {

    private final ImportBatchRepository batches;
    private final BoundedCsvParser parser;
    private final CsvImportTransactionService transaction;
    private final ImportBatchFailureService failures;

    CsvImportCoordinator(
            ImportBatchRepository batches,
            BoundedCsvParser parser,
            CsvImportTransactionService transaction,
            ImportBatchFailureService failures) {
        this.batches = batches;
        this.parser = parser;
        this.transaction = transaction;
        this.failures = failures;
    }

    public CsvImportResult importCsv(
            ImportBatchId batchId,
            String fileName,
            String contentType,
            long size,
            InputStream input) {
        ImportBatch batch = batches.findById(batchId)
                .orElseThrow(ImportBatchNotFoundException::new);
        if (batch.status() == ImportBatchStatus.COMPLETED) {
            return new CsvImportResult(batch, true);
        }
        if (batch.status() != ImportBatchStatus.REGISTERED) {
            throw new CsvImportException(
                    "IMPORT_BATCH_STATE_CONFLICT", false, "import batch cannot be imported in its current state");
        }
        validateMetadata(batch, fileName, contentType, size);

        CsvImportPlan plan;
        try {
            plan = parser.parse(Objects.requireNonNull(input, "CSV input must not be null"), batch);
        } catch (CsvImportException exception) {
            if (exception.terminal()) {
                failures.failRegistered(batchId, exception.code(), 1);
            }
            throw exception;
        }
        if (!plan.sourceSha256().equals(batch.sourceSha256())) {
            throw new CsvImportException(
                    "IMPORT_SOURCE_CHECKSUM_MISMATCH", false, "uploaded CSV checksum differs from registration");
        }
        try {
            return new CsvImportResult(transaction.commit(batchId, plan), false);
        } catch (CsvImportException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            failures.failRegistered(batchId, "IMPORT_TRANSACTION_FAILED", plan.dataRecordCount());
            throw new CsvImportException(
                    "IMPORT_TRANSACTION_FAILED", false, "CSV import transaction failed");
        }
    }

    private static void validateMetadata(
            ImportBatch batch,
            String fileName,
            String contentType,
            long size) {
        if (batch.sourceType() != ImportSourceType.CSV
                || !batch.sourceName().equals(fileName)
                || !batch.contentType().equals(contentType)
                || batch.sourceSizeBytes() != size) {
            throw new CsvImportException(
                    "IMPORT_SOURCE_METADATA_MISMATCH", false, "uploaded CSV metadata differs from registration");
        }
    }
}
