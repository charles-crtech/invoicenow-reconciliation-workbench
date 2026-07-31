package io.github.charlescrtech.invoicenow.reconciliation.infrastructure.persistence;

import io.github.charlescrtech.invoicenow.reconciliation.application.LedgerEntryRepository;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerEntry;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerEntryId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaLedgerEntryRepositoryAdapter implements LedgerEntryRepository {

    private final SpringDataLedgerEntryRepository repository;

    JpaLedgerEntryRepositoryAdapter(SpringDataLedgerEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public LedgerEntry save(LedgerEntry entry) {
        Objects.requireNonNull(entry, "ledger entry must not be null");
        return repository.saveAndFlush(LedgerEntryPersistenceEntity.fromDomain(entry)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LedgerEntry> findById(LedgerEntryId id) {
        Objects.requireNonNull(id, "ledger entry ID must not be null");
        return repository.findById(id.value()).map(LedgerEntryPersistenceEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LedgerEntry> findBySourceIdentity(
            SourceSystemCode sourceSystem,
            SourceRecordId sourceRecordId) {
        Objects.requireNonNull(sourceSystem, "source system must not be null");
        Objects.requireNonNull(sourceRecordId, "source record ID must not be null");
        return repository.findBySourceSystemAndSourceRecordId(sourceSystem.value(), sourceRecordId.value())
                .map(LedgerEntryPersistenceEntity::toDomain);
    }
}
