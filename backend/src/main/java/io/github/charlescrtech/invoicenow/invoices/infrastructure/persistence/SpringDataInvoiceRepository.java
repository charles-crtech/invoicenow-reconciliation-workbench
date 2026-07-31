package io.github.charlescrtech.invoicenow.invoices.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataInvoiceRepository extends JpaRepository<InvoicePersistenceEntity, UUID> {

    Optional<InvoicePersistenceEntity> findBySourceSystemAndSourceRecordId(
            String sourceSystem,
            String sourceRecordId);
}
