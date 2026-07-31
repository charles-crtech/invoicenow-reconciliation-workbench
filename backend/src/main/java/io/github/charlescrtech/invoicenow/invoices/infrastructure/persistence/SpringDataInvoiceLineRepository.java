package io.github.charlescrtech.invoicenow.invoices.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataInvoiceLineRepository
        extends JpaRepository<InvoiceLinePersistenceEntity, InvoiceLinePersistenceId> {

    List<InvoiceLinePersistenceEntity> findAllByIdInvoiceIdOrderByIdLineNumber(UUID invoiceId);

    long deleteAllByIdInvoiceId(UUID invoiceId);
}
