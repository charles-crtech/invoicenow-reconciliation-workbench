package io.github.charlescrtech.invoicenow.reconciliation.domain;

import io.github.charlescrtech.invoicenow.shared.domain.money.Money;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import io.github.charlescrtech.invoicenow.shared.domain.time.ReportingPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.OptionalLong;

/** Immutable normalized ledger evidence; reversals are separate entries rather than mutations. */
public final class LedgerEntry {

    private static final BigDecimal MAX_DATABASE_AMOUNT = new BigDecimal("999999999999999.9999");

    private final LedgerEntryId id;
    private final SourceSystemCode sourceSystem;
    private final SourceRecordId sourceRecordId;
    private final AccountCode accountCode;
    private final CounterpartyReference counterpartyReference;
    private final LedgerDocumentReference documentReference;
    private final LocalDate postingDate;
    private final ReportingPeriod reportingPeriod;
    private final Money debitAmount;
    private final Money creditAmount;
    private final Money taxAmount;
    private final Sha256Hash sourcePayloadHash;
    private final Long version;
    private final Instant recordedAt;

    private LedgerEntry(
            LedgerEntryId id,
            SourceSystemCode sourceSystem,
            SourceRecordId sourceRecordId,
            AccountCode accountCode,
            CounterpartyReference counterpartyReference,
            LedgerDocumentReference documentReference,
            LocalDate postingDate,
            ReportingPeriod reportingPeriod,
            Money debitAmount,
            Money creditAmount,
            Money taxAmount,
            Sha256Hash sourcePayloadHash,
            Long version,
            Instant recordedAt) {
        this.id = Objects.requireNonNull(id, "ledger entry ID must not be null");
        this.sourceSystem = Objects.requireNonNull(sourceSystem, "source system must not be null");
        this.sourceRecordId = Objects.requireNonNull(sourceRecordId, "source record ID must not be null");
        this.accountCode = Objects.requireNonNull(accountCode, "account code must not be null");
        this.counterpartyReference = Objects.requireNonNull(
                counterpartyReference,
                "counterparty reference must not be null");
        this.documentReference = Objects.requireNonNull(
                documentReference,
                "ledger document reference must not be null");
        this.postingDate = Objects.requireNonNull(postingDate, "posting date must not be null");
        this.reportingPeriod = Objects.requireNonNull(reportingPeriod, "reporting period must not be null");
        this.debitAmount = Objects.requireNonNull(debitAmount, "debit amount must not be null");
        this.creditAmount = Objects.requireNonNull(creditAmount, "credit amount must not be null");
        this.taxAmount = Objects.requireNonNull(taxAmount, "tax amount must not be null");
        this.sourcePayloadHash = Objects.requireNonNull(sourcePayloadHash, "source payload hash must not be null");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        validateAmounts();
        if (version != null && version < 0) {
            throw new IllegalArgumentException("ledger entry version must not be negative");
        }
        this.version = version;
    }

    public static LedgerEntry create(
            SourceSystemCode sourceSystem,
            SourceRecordId sourceRecordId,
            AccountCode accountCode,
            CounterpartyReference counterpartyReference,
            LedgerDocumentReference documentReference,
            LocalDate postingDate,
            ReportingPeriod reportingPeriod,
            Money debitAmount,
            Money creditAmount,
            Money taxAmount,
            Sha256Hash sourcePayloadHash,
            Instant recordedAt) {
        return create(
                LedgerEntryId.newId(), sourceSystem, sourceRecordId, accountCode, counterpartyReference,
                documentReference, postingDate, reportingPeriod, debitAmount, creditAmount, taxAmount,
                sourcePayloadHash, recordedAt);
    }

    public static LedgerEntry create(
            LedgerEntryId id,
            SourceSystemCode sourceSystem,
            SourceRecordId sourceRecordId,
            AccountCode accountCode,
            CounterpartyReference counterpartyReference,
            LedgerDocumentReference documentReference,
            LocalDate postingDate,
            ReportingPeriod reportingPeriod,
            Money debitAmount,
            Money creditAmount,
            Money taxAmount,
            Sha256Hash sourcePayloadHash,
            Instant recordedAt) {
        return new LedgerEntry(
                id, sourceSystem, sourceRecordId, accountCode, counterpartyReference, documentReference,
                postingDate, reportingPeriod, debitAmount, creditAmount, taxAmount, sourcePayloadHash,
                null, recordedAt);
    }

    public static LedgerEntry restore(
            LedgerEntryId id,
            SourceSystemCode sourceSystem,
            SourceRecordId sourceRecordId,
            AccountCode accountCode,
            CounterpartyReference counterpartyReference,
            LedgerDocumentReference documentReference,
            LocalDate postingDate,
            ReportingPeriod reportingPeriod,
            Money debitAmount,
            Money creditAmount,
            Money taxAmount,
            Sha256Hash sourcePayloadHash,
            long version,
            Instant recordedAt) {
        return new LedgerEntry(
                id, sourceSystem, sourceRecordId, accountCode, counterpartyReference, documentReference,
                postingDate, reportingPeriod, debitAmount, creditAmount, taxAmount, sourcePayloadHash,
                version, recordedAt);
    }

    private void validateAmounts() {
        Currency currency = debitAmount.currency();
        if (!currency.equals(creditAmount.currency()) || !currency.equals(taxAmount.currency())) {
            throw new IllegalArgumentException("all ledger monetary values must use one currency");
        }
        requireNonNegativeAndBounded(debitAmount, "debit amount");
        requireNonNegativeAndBounded(creditAmount, "credit amount");
        requireNonNegativeAndBounded(taxAmount, "tax amount");
        boolean hasDebit = !debitAmount.isZero();
        boolean hasCredit = !creditAmount.isZero();
        if (hasDebit == hasCredit) {
            throw new IllegalArgumentException("exactly one of debit or credit amount must be positive");
        }
    }

    private static void requireNonNegativeAndBounded(Money amount, String label) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
        if (amount.amount().compareTo(MAX_DATABASE_AMOUNT) > 0) {
            throw new IllegalArgumentException(label + " exceeds the supported numeric range");
        }
    }

    public LedgerEntrySide side() {
        return debitAmount.isZero() ? LedgerEntrySide.CREDIT : LedgerEntrySide.DEBIT;
    }

    /** Debit is positive and credit is negative; later matching policy decides how to compare it. */
    public Money signedAmount() {
        return debitAmount.minus(creditAmount);
    }

    public Money signedTaxAmount() {
        return side() == LedgerEntrySide.DEBIT ? taxAmount : taxAmount.negate();
    }

    public boolean postingDateMatchesReportingPeriod() {
        return reportingPeriod.contains(postingDate);
    }

    public LedgerEntryId id() { return id; }
    public SourceSystemCode sourceSystem() { return sourceSystem; }
    public SourceRecordId sourceRecordId() { return sourceRecordId; }
    public AccountCode accountCode() { return accountCode; }
    public CounterpartyReference counterpartyReference() { return counterpartyReference; }
    public LedgerDocumentReference documentReference() { return documentReference; }
    public LocalDate postingDate() { return postingDate; }
    public ReportingPeriod reportingPeriod() { return reportingPeriod; }
    public Money debitAmount() { return debitAmount; }
    public Money creditAmount() { return creditAmount; }
    public Money taxAmount() { return taxAmount; }
    public Sha256Hash sourcePayloadHash() { return sourcePayloadHash; }
    public Currency currency() { return debitAmount.currency(); }
    public OptionalLong version() { return version == null ? OptionalLong.empty() : OptionalLong.of(version); }
    public Instant recordedAt() { return recordedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof LedgerEntry entry && id.equals(entry.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LedgerEntry[id=" + id + ", account=" + accountCode + ", side=" + side()
                + ", postingDate=" + postingDate + "]";
    }
}
