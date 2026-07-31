package io.github.charlescrtech.invoicenow.reconciliation.infrastructure.persistence;

import io.github.charlescrtech.invoicenow.reconciliation.domain.AccountCode;
import io.github.charlescrtech.invoicenow.reconciliation.domain.CounterpartyReference;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerDocumentReference;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerEntry;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerEntryId;
import io.github.charlescrtech.invoicenow.shared.domain.money.Money;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import io.github.charlescrtech.invoicenow.shared.domain.time.ReportingPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", schema = "app")
class LedgerEntryPersistenceEntity {

    @Id
    private UUID id;

    @Column(name = "source_system", nullable = false, length = 32)
    private String sourceSystem;

    @Column(name = "source_record_id", nullable = false, length = 100)
    private String sourceRecordId;

    @Column(name = "account_code", nullable = false, length = 32)
    private String accountCode;

    @Column(name = "counterparty_reference", nullable = false, length = 64)
    private String counterpartyReference;

    @Column(name = "document_reference", nullable = false, length = 64)
    private String documentReference;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "reporting_period_start", nullable = false)
    private LocalDate reportingPeriodStart;

    @Column(name = "reporting_period_end", nullable = false)
    private LocalDate reportingPeriodEnd;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "debit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditAmount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "source_payload_hash", nullable = false, length = 64)
    private String sourcePayloadHash;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected LedgerEntryPersistenceEntity() {
    }

    private LedgerEntryPersistenceEntity(
            UUID id,
            String sourceSystem,
            String sourceRecordId,
            String accountCode,
            String counterpartyReference,
            String documentReference,
            LocalDate postingDate,
            LocalDate reportingPeriodStart,
            LocalDate reportingPeriodEnd,
            String currency,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            BigDecimal taxAmount,
            String sourcePayloadHash,
            Long version,
            Instant recordedAt) {
        this.id = id;
        this.sourceSystem = sourceSystem;
        this.sourceRecordId = sourceRecordId;
        this.accountCode = accountCode;
        this.counterpartyReference = counterpartyReference;
        this.documentReference = documentReference;
        this.postingDate = postingDate;
        this.reportingPeriodStart = reportingPeriodStart;
        this.reportingPeriodEnd = reportingPeriodEnd;
        this.currency = currency;
        this.debitAmount = debitAmount;
        this.creditAmount = creditAmount;
        this.taxAmount = taxAmount;
        this.sourcePayloadHash = sourcePayloadHash;
        this.version = version;
        this.recordedAt = recordedAt;
    }

    static LedgerEntryPersistenceEntity fromDomain(LedgerEntry entry) {
        return new LedgerEntryPersistenceEntity(
                entry.id().value(),
                entry.sourceSystem().value(),
                entry.sourceRecordId().value(),
                entry.accountCode().value(),
                entry.counterpartyReference().value(),
                entry.documentReference().value(),
                entry.postingDate(),
                entry.reportingPeriod().startInclusive(),
                entry.reportingPeriod().endExclusive(),
                entry.currency().getCurrencyCode(),
                entry.debitAmount().amount(),
                entry.creditAmount().amount(),
                entry.taxAmount().amount(),
                entry.sourcePayloadHash().value(),
                entry.version().isPresent() ? entry.version().getAsLong() : null,
                entry.recordedAt());
    }

    LedgerEntry toDomain() {
        Currency persistedCurrency = Currency.getInstance(currency);
        return LedgerEntry.restore(
                new LedgerEntryId(id),
                new SourceSystemCode(sourceSystem),
                new SourceRecordId(sourceRecordId),
                new AccountCode(accountCode),
                new CounterpartyReference(counterpartyReference),
                new LedgerDocumentReference(documentReference),
                postingDate,
                new ReportingPeriod(reportingPeriodStart, reportingPeriodEnd),
                new Money(debitAmount, persistedCurrency),
                new Money(creditAmount, persistedCurrency),
                new Money(taxAmount, persistedCurrency),
                new Sha256Hash(sourcePayloadHash),
                version,
                recordedAt);
    }
}
