package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.application.RegisterImportBatchCommand.ImportBatchDraft;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportBatchService {

    private final ImportBatchRepository repository;
    private final Clock clock;

    public ImportBatchService(ImportBatchRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ImportBatchRegistration register(RegisterImportBatchCommand command) {
        Objects.requireNonNull(command, "register import command must not be null");
        ImportBatchDraft draft = command.toDraft();
        ImportBatch candidate = ImportBatch.register(
                ImportBatchId.newId(),
                draft.datasetId(),
                draft.contractVersion(),
                draft.sourceType(),
                draft.sourceName(),
                draft.contentType(),
                draft.sourceSizeBytes(),
                draft.sourceSha256(),
                draft.manifestSha256(),
                draft.idempotencyKey(),
                Instant.now(clock));

        ImportBatch byKey = repository.findByIdempotencyKey(draft.idempotencyKey()).orElse(null);
        if (byKey != null) {
            return replayForSameKey(byKey, candidate);
        }
        ImportBatch byContent = repository.findByFingerprint(candidate.fingerprint()).orElse(null);
        if (byContent != null) {
            return new ImportBatchRegistration(byContent, true);
        }

        if (repository.insertIfAbsent(candidate)) {
            ImportBatch inserted = repository.findById(candidate.id())
                    .orElseThrow(() -> new IllegalStateException("inserted import batch cannot be resolved"));
            return new ImportBatchRegistration(inserted, false);
        }

        byKey = repository.findByIdempotencyKey(draft.idempotencyKey()).orElse(null);
        if (byKey != null) {
            return replayForSameKey(byKey, candidate);
        }
        byContent = repository.findByFingerprint(candidate.fingerprint()).orElse(null);
        if (byContent != null) {
            return new ImportBatchRegistration(byContent, true);
        }
        throw new IllegalStateException("conflicting import batch cannot be resolved");
    }

    @Transactional(readOnly = true)
    public ImportBatch get(ImportBatchId id) {
        Objects.requireNonNull(id, "import batch ID must not be null");
        return repository.findById(id).orElseThrow(ImportBatchNotFoundException::new);
    }

    private static ImportBatchRegistration replayForSameKey(ImportBatch existing, ImportBatch candidate) {
        if (!existing.hasSameRegistrationDetails(candidate)) {
            throw new ImportIdempotencyConflictException();
        }
        return new ImportBatchRegistration(existing, true);
    }
}
