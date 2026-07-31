package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import io.github.charlescrtech.invoicenow.imports.domain.QuarantineRecord;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuarantineQueryService {

    private final ImportBatchRepository batches;
    private final QuarantineRepository quarantine;

    QuarantineQueryService(ImportBatchRepository batches, QuarantineRepository quarantine) {
        this.batches = batches;
        this.quarantine = quarantine;
    }

    @Transactional(readOnly = true)
    public QuarantinePage get(ImportBatchId batchId, int limit, int offset) {
        if (batches.findById(batchId).isEmpty()) {
            throw new ImportBatchNotFoundException();
        }
        List<QuarantineRecord> records = quarantine.findByBatch(batchId, limit, offset);
        return new QuarantinePage(records, quarantine.countByBatch(batchId));
    }

    public record QuarantinePage(List<QuarantineRecord> records, long total) {
        public QuarantinePage {
            records = List.copyOf(records);
        }
    }
}
