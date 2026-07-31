package io.github.charlescrtech.invoicenow.invoices.infrastructure.persistence;

import io.github.charlescrtech.invoicenow.invoices.domain.CorrectionReason;
import io.github.charlescrtech.invoicenow.invoices.domain.DocumentNumber;
import io.github.charlescrtech.invoicenow.invoices.domain.DocumentType;
import io.github.charlescrtech.invoicenow.invoices.domain.Invoice;
import io.github.charlescrtech.invoicenow.invoices.domain.InvoiceId;
import io.github.charlescrtech.invoicenow.invoices.domain.InvoiceLine;
import io.github.charlescrtech.invoicenow.invoices.domain.InvoiceStatus;
import io.github.charlescrtech.invoicenow.shared.domain.money.Money;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import io.github.charlescrtech.invoicenow.shared.domain.time.ReportingPeriod;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices", schema = "app")
class InvoicePersistenceEntity {

    @Id
    private UUID id;

    @Column(name = "source_system", nullable = false, length = 32)
    private String sourceSystem;

    @Column(name = "source_record_id", nullable = false, length = 100)
    private String sourceRecordId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "document_number", nullable = false, length = 64)
    private String documentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 16)
    private DocumentType documentType;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "reporting_period_start", nullable = false)
    private LocalDate reportingPeriodStart;

    @Column(name = "reporting_period_end", nullable = false)
    private LocalDate reportingPeriodEnd;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "declared_net", nullable = false, precision = 19, scale = 4)
    private BigDecimal declaredNet;

    @Column(name = "declared_tax", nullable = false, precision = 19, scale = 4)
    private BigDecimal declaredTax;

    @Column(name = "declared_gross", nullable = false, precision = 19, scale = 4)
    private BigDecimal declaredGross;

    @Column(name = "source_payload_hash", nullable = false, length = 64)
    private String sourcePayloadHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_status", nullable = false, length = 16)
    private InvoiceStatus recordStatus;

    @Column(name = "last_correction_reason", length = CorrectionReason.MAX_LENGTH)
    private String lastCorrectionReason;

    @Column(name = "void_reason", length = CorrectionReason.MAX_LENGTH)
    private String voidReason;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InvoicePersistenceEntity() {
    }

    private InvoicePersistenceEntity(
            UUID id,
            String sourceSystem,
            String sourceRecordId,
            UUID supplierId,
            String documentNumber,
            DocumentType documentType,
            LocalDate issueDate,
            LocalDate postingDate,
            LocalDate reportingPeriodStart,
            LocalDate reportingPeriodEnd,
            String currency,
            BigDecimal declaredNet,
            BigDecimal declaredTax,
            BigDecimal declaredGross,
            String sourcePayloadHash,
            InvoiceStatus recordStatus,
            String lastCorrectionReason,
            String voidReason,
            Long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.sourceSystem = sourceSystem;
        this.sourceRecordId = sourceRecordId;
        this.supplierId = supplierId;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
        this.issueDate = issueDate;
        this.postingDate = postingDate;
        this.reportingPeriodStart = reportingPeriodStart;
        this.reportingPeriodEnd = reportingPeriodEnd;
        this.currency = currency;
        this.declaredNet = declaredNet;
        this.declaredTax = declaredTax;
        this.declaredGross = declaredGross;
        this.sourcePayloadHash = sourcePayloadHash;
        this.recordStatus = recordStatus;
        this.lastCorrectionReason = lastCorrectionReason;
        this.voidReason = voidReason;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static InvoicePersistenceEntity fromDomain(Invoice invoice) {
        return new InvoicePersistenceEntity(
                invoice.id().value(),
                invoice.sourceSystem().value(),
                invoice.sourceRecordId().value(),
                invoice.supplierId().value(),
                invoice.documentNumber().value(),
                invoice.documentType(),
                invoice.issueDate(),
                invoice.postingDate(),
                invoice.reportingPeriod().startInclusive(),
                invoice.reportingPeriod().endExclusive(),
                invoice.currency().getCurrencyCode(),
                invoice.declaredNetTotal().amount(),
                invoice.declaredTaxTotal().amount(),
                invoice.declaredGrossTotal().amount(),
                invoice.sourcePayloadHash().value(),
                invoice.status(),
                invoice.lastCorrectionReason().map(CorrectionReason::value).orElse(null),
                invoice.voidReason().map(CorrectionReason::value).orElse(null),
                invoice.version().isPresent() ? invoice.version().getAsLong() : null,
                invoice.createdAt(),
                invoice.updatedAt());
    }

    Invoice toDomain(List<InvoiceLine> lines) {
        Currency persistedCurrency = Currency.getInstance(currency);
        return Invoice.restore(
                new InvoiceId(id),
                new SourceSystemCode(sourceSystem),
                new SourceRecordId(sourceRecordId),
                new SupplierId(supplierId),
                new DocumentNumber(documentNumber),
                documentType,
                issueDate,
                postingDate,
                new ReportingPeriod(reportingPeriodStart, reportingPeriodEnd),
                new Money(declaredNet, persistedCurrency),
                new Money(declaredTax, persistedCurrency),
                new Money(declaredGross, persistedCurrency),
                new Sha256Hash(sourcePayloadHash),
                recordStatus,
                lines,
                lastCorrectionReason == null ? null : new CorrectionReason(lastCorrectionReason),
                voidReason == null ? null : new CorrectionReason(voidReason),
                version,
                createdAt,
                updatedAt);
    }

    UUID id() {
        return id;
    }

    String currency() {
        return currency;
    }
}
