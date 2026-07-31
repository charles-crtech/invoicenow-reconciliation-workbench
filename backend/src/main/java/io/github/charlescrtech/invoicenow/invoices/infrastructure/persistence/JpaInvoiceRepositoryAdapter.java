package io.github.charlescrtech.invoicenow.invoices.infrastructure.persistence;

import io.github.charlescrtech.invoicenow.invoices.application.InvoiceRepository;
import io.github.charlescrtech.invoicenow.invoices.domain.Invoice;
import io.github.charlescrtech.invoicenow.invoices.domain.InvoiceId;
import io.github.charlescrtech.invoicenow.invoices.domain.InvoiceLine;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaInvoiceRepositoryAdapter implements InvoiceRepository {

    private final SpringDataInvoiceRepository invoices;
    private final SpringDataInvoiceLineRepository lines;

    JpaInvoiceRepositoryAdapter(
            SpringDataInvoiceRepository invoices,
            SpringDataInvoiceLineRepository lines) {
        this.invoices = invoices;
        this.lines = lines;
    }

    @Override
    @Transactional
    public Invoice save(Invoice invoice) {
        Objects.requireNonNull(invoice, "invoice must not be null");
        InvoicePersistenceEntity saved = invoices.saveAndFlush(InvoicePersistenceEntity.fromDomain(invoice));
        lines.deleteAllByIdInvoiceId(saved.id());
        lines.flush();
        List<InvoiceLinePersistenceEntity> savedLines = lines.saveAllAndFlush(invoice.lines().stream()
                .map(line -> InvoiceLinePersistenceEntity.fromDomain(saved.id(), line))
                .toList());
        return saved.toDomain(toDomainLines(saved, savedLines));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findById(InvoiceId id) {
        Objects.requireNonNull(id, "invoice ID must not be null");
        return invoices.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findBySourceIdentity(
            SourceSystemCode sourceSystem,
            SourceRecordId sourceRecordId) {
        Objects.requireNonNull(sourceSystem, "source system must not be null");
        Objects.requireNonNull(sourceRecordId, "source record ID must not be null");
        return invoices.findBySourceSystemAndSourceRecordId(sourceSystem.value(), sourceRecordId.value())
                .map(this::toDomain);
    }

    private Invoice toDomain(InvoicePersistenceEntity entity) {
        List<InvoiceLinePersistenceEntity> persistedLines =
                lines.findAllByIdInvoiceIdOrderByIdLineNumber(entity.id());
        return entity.toDomain(toDomainLines(entity, persistedLines));
    }

    private static List<InvoiceLine> toDomainLines(
            InvoicePersistenceEntity invoice,
            List<InvoiceLinePersistenceEntity> persistedLines) {
        return persistedLines.stream()
                .map(line -> line.toDomain(invoice.currency()))
                .toList();
    }
}
