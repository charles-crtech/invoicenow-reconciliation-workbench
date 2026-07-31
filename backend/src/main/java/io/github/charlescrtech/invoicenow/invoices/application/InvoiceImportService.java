package io.github.charlescrtech.invoicenow.invoices.application;

import io.github.charlescrtech.invoicenow.invoices.domain.Invoice;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Explicit invoice-module API used by transactional import orchestration. */
@Service
public class InvoiceImportService {

    private final InvoiceRepository repository;

    InvoiceImportService(InvoiceRepository repository) {
        this.repository = repository;
    }

    public Invoice save(Invoice invoice) {
        return repository.save(invoice);
    }

    public Optional<Invoice> findBySourceIdentity(
            SourceSystemCode sourceSystem,
            SourceRecordId sourceRecordId) {
        return repository.findBySourceIdentity(sourceSystem, sourceRecordId);
    }
}
