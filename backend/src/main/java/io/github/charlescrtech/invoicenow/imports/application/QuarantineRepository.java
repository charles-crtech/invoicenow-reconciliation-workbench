package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import io.github.charlescrtech.invoicenow.imports.domain.QuarantineRecord;
import java.util.List;

public interface QuarantineRepository {

    void saveAll(List<QuarantineRecord> records);

    List<QuarantineRecord> findByBatch(ImportBatchId batchId, int limit, int offset);

    long countByBatch(ImportBatchId batchId);
}
