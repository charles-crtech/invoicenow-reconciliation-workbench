package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.domain.IdempotencyKey;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchFingerprint;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import java.util.Optional;

public interface ImportBatchRepository {

    boolean insertIfAbsent(ImportBatch batch);

    Optional<ImportBatch> findById(ImportBatchId id);

    Optional<ImportBatch> findByIdempotencyKey(IdempotencyKey key);

    Optional<ImportBatch> findByFingerprint(ImportBatchFingerprint fingerprint);
}
