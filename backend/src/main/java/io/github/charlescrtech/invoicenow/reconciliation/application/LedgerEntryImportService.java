package io.github.charlescrtech.invoicenow.reconciliation.application;

import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerEntry;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Explicit reconciliation-module API used by transactional import orchestration. */
@Service
public class LedgerEntryImportService {

    private final LedgerEntryRepository repository;

    LedgerEntryImportService(LedgerEntryRepository repository) {
        this.repository = repository;
    }

    public LedgerEntry save(LedgerEntry entry) {
        return repository.save(entry);
    }

    public Optional<LedgerEntry> findBySourceIdentity(
            SourceSystemCode sourceSystem,
            SourceRecordId sourceRecordId) {
        return repository.findBySourceIdentity(sourceSystem, sourceRecordId);
    }
}
