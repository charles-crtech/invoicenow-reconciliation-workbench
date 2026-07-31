package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportBatchFailureService {

    private final ImportBatchRepository repository;
    private final Clock clock;

    ImportBatchFailureService(ImportBatchRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportBatch failRegistered(ImportBatchId batchId, String code, long rejectedCount) {
        ImportBatch current = repository.findById(batchId)
                .orElseThrow(ImportBatchNotFoundException::new);
        if (current.status() == ImportBatchStatus.FAILED) {
            return current;
        }
        if (current.status() != ImportBatchStatus.REGISTERED) {
            throw new CsvImportException(
                    "IMPORT_BATCH_STATE_CONFLICT", false, "import batch is not registered");
        }
        Instant now = clock.instant();
        ImportBatch processing = repository.update(current.start(now))
                .orElseThrow(ImportBatchFailureService::concurrentChange);
        return repository.update(processing.fail(code, rejectedCount, clock.instant()))
                .orElseThrow(ImportBatchFailureService::concurrentChange);
    }

    private static CsvImportException concurrentChange() {
        return new CsvImportException(
                "IMPORT_BATCH_STATE_CONFLICT", false, "import batch changed concurrently");
    }
}
