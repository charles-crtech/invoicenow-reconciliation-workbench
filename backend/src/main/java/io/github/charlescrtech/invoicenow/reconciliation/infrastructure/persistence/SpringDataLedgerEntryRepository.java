package io.github.charlescrtech.invoicenow.reconciliation.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataLedgerEntryRepository extends JpaRepository<LedgerEntryPersistenceEntity, UUID> {

    Optional<LedgerEntryPersistenceEntity> findBySourceSystemAndSourceRecordId(
            String sourceSystem,
            String sourceRecordId);
}
