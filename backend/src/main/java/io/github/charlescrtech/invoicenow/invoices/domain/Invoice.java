package io.github.charlescrtech.invoicenow.invoices.domain;

import io.github.charlescrtech.invoicenow.shared.domain.money.Money;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import io.github.charlescrtech.invoicenow.shared.domain.time.ReportingPeriod;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable invoice aggregate that preserves declared source values and exposes discrepancies. */
public final class Invoice {

    private final InvoiceId id;
    private final SourceSystemCode sourceSystem;
    private final SourceRecordId sourceRecordId;
    private final SupplierId supplierId;
    private final DocumentNumber documentNumber;
    private final DocumentType documentType;
    private final LocalDate issueDate;
    private final LocalDate postingDate;
    private final ReportingPeriod reportingPeriod;
    private final Money declaredNetTotal;
    private final Money declaredTaxTotal;
    private final Money declaredGrossTotal;
    private final Sha256Hash sourcePayloadHash;
    private final InvoiceStatus status;
    private final List<InvoiceLine> lines;
    private final CorrectionReason lastCorrectionReason;
    private final CorrectionReason voidReason;
    private final Long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Invoice(
            InvoiceId id,
            SourceSystemCode sourceSystem,
            SourceRecordId sourceRecordId,
            SupplierId supplierId,
            DocumentNumber documentNumber,
            DocumentType documentType,
            LocalDate issueDate,
            LocalDate postingDate,
            ReportingPeriod reportingPeriod,
            Money declaredNetTotal,
            Money declaredTaxTotal,
            Money declaredGrossTotal,
            Sha256Hash sourcePayloadHash,
            InvoiceStatus status,
            List<InvoiceLine> lines,
            CorrectionReason lastCorrectionReason,
            CorrectionReason voidReason,
            Long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "invoice ID must not be null");
        this.sourceSystem = Objects.requireNonNull(sourceSystem, "source system must not be null");
        this.sourceRecordId = Objects.requireNonNull(sourceRecordId, "source record ID must not be null");
        this.supplierId = Objects.requireNonNull(supplierId, "supplier ID must not be null");
        this.documentNumber = Objects.requireNonNull(documentNumber, "document number must not be null");
        this.documentType = Objects.requireNonNull(documentType, "document type must not be null");
        this.issueDate = Objects.requireNonNull(issueDate, "issue date must not be null");
        this.postingDate = Objects.requireNonNull(postingDate, "posting date must not be null");
        this.reportingPeriod = Objects.requireNonNull(reportingPeriod, "reporting period must not be null");
        this.declaredNetTotal = Objects.requireNonNull(declaredNetTotal, "declared net total must not be null");
        this.declaredTaxTotal = Objects.requireNonNull(declaredTaxTotal, "declared tax total must not be null");
        this.declaredGrossTotal = Objects.requireNonNull(declaredGrossTotal, "declared gross total must not be null");
        this.sourcePayloadHash = Objects.requireNonNull(sourcePayloadHash, "source payload hash must not be null");
        this.status = Objects.requireNonNull(status, "invoice status must not be null");
        this.lines = validateAndOrderLines(lines, declaredNetTotal.currency());
        this.lastCorrectionReason = lastCorrectionReason;
        this.voidReason = voidReason;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        requireValidState(version);
        this.version = version;
    }

    public static Invoice create(
            SourceSystemCode sourceSystem,
            SourceRecordId sourceRecordId,
            SupplierId supplierId,
            DocumentNumber documentNumber,
            DocumentType documentType,
            LocalDate issueDate,
            LocalDate postingDate,
            ReportingPeriod reportingPeriod,
            Money declaredNetTotal,
            Money declaredTaxTotal,
            Money declaredGrossTotal,
            Sha256Hash sourcePayloadHash,
            List<InvoiceLine> lines,
            Instant createdAt) {
        return create(
                InvoiceId.newId(), sourceSystem, sourceRecordId, supplierId, documentNumber, documentType,
                issueDate, postingDate, reportingPeriod, declaredNetTotal, declaredTaxTotal,
                declaredGrossTotal, sourcePayloadHash, lines, createdAt);
    }

    public static Invoice create(
            InvoiceId id,
            SourceSystemCode sourceSystem,
            SourceRecordId sourceRecordId,
            SupplierId supplierId,
            DocumentNumber documentNumber,
            DocumentType documentType,
            LocalDate issueDate,
            LocalDate postingDate,
            ReportingPeriod reportingPeriod,
            Money declaredNetTotal,
            Money declaredTaxTotal,
            Money declaredGrossTotal,
            Sha256Hash sourcePayloadHash,
            List<InvoiceLine> lines,
            Instant createdAt) {
        return new Invoice(
                id, sourceSystem, sourceRecordId, supplierId, documentNumber, documentType, issueDate,
                postingDate, reportingPeriod, declaredNetTotal, declaredTaxTotal, declaredGrossTotal,
                sourcePayloadHash, InvoiceStatus.ACTIVE, lines, null, null, null, createdAt, createdAt);
    }

    public static Invoice restore(
            InvoiceId id,
            SourceSystemCode sourceSystem,
            SourceRecordId sourceRecordId,
            SupplierId supplierId,
            DocumentNumber documentNumber,
            DocumentType documentType,
            LocalDate issueDate,
            LocalDate postingDate,
            ReportingPeriod reportingPeriod,
            Money declaredNetTotal,
            Money declaredTaxTotal,
            Money declaredGrossTotal,
            Sha256Hash sourcePayloadHash,
            InvoiceStatus status,
            List<InvoiceLine> lines,
            CorrectionReason lastCorrectionReason,
            CorrectionReason voidReason,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        return new Invoice(
                id, sourceSystem, sourceRecordId, supplierId, documentNumber, documentType, issueDate,
                postingDate, reportingPeriod, declaredNetTotal, declaredTaxTotal, declaredGrossTotal,
                sourcePayloadHash, status, lines, lastCorrectionReason, voidReason, version, createdAt, updatedAt);
    }

    public Invoice correct(
            LocalDate newIssueDate,
            LocalDate newPostingDate,
            ReportingPeriod newReportingPeriod,
            Money newDeclaredNetTotal,
            Money newDeclaredTaxTotal,
            Money newDeclaredGrossTotal,
            List<InvoiceLine> newLines,
            CorrectionReason reason,
            Instant changedAt) {
        ensureActive("only an active invoice can be corrected");
        Objects.requireNonNull(reason, "correction reason must not be null");
        validateChangeTime(changedAt);
        return new Invoice(
                id, sourceSystem, sourceRecordId, supplierId, documentNumber, documentType, newIssueDate,
                newPostingDate, newReportingPeriod, newDeclaredNetTotal, newDeclaredTaxTotal,
                newDeclaredGrossTotal, sourcePayloadHash, status, newLines, reason, null, version,
                createdAt, changedAt);
    }

    public Invoice voidInvoice(CorrectionReason reason, Instant changedAt) {
        ensureActive("only an active invoice can be voided");
        Objects.requireNonNull(reason, "void reason must not be null");
        validateChangeTime(changedAt);
        return new Invoice(
                id, sourceSystem, sourceRecordId, supplierId, documentNumber, documentType, issueDate,
                postingDate, reportingPeriod, declaredNetTotal, declaredTaxTotal, declaredGrossTotal,
                sourcePayloadHash, InvoiceStatus.VOIDED, lines, lastCorrectionReason, reason, version,
                createdAt, changedAt);
    }

    public Money calculatedNetTotal() {
        return sumLines(InvoiceLine::netAmount);
    }

    public Money calculatedTaxTotal() {
        return sumLines(InvoiceLine::taxAmount);
    }

    public Money calculatedGrossTotal() {
        return sumLines(InvoiceLine::grossAmount);
    }

    public Money netDifference() {
        return declaredNetTotal.minus(calculatedNetTotal());
    }

    public Money taxDifference() {
        return declaredTaxTotal.minus(calculatedTaxTotal());
    }

    public Money grossDifference() {
        return declaredGrossTotal.minus(calculatedGrossTotal());
    }

    private Money sumLines(java.util.function.Function<InvoiceLine, Money> amount) {
        Money total = Money.zero(currency().getCurrencyCode());
        for (InvoiceLine line : lines) {
            total = total.plus(amount.apply(line));
        }
        return total;
    }

    private void requireValidState(Long candidateVersion) {
        if (postingDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("posting date must not be before issue date");
        }
        if (!declaredNetTotal.currency().equals(declaredTaxTotal.currency())
                || !declaredNetTotal.currency().equals(declaredGrossTotal.currency())) {
            throw new IllegalArgumentException("all invoice monetary values must use one currency");
        }
        if (candidateVersion != null && candidateVersion < 0) {
            throw new IllegalArgumentException("invoice version must not be negative");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (status == InvoiceStatus.ACTIVE && voidReason != null) {
            throw new IllegalArgumentException("an active invoice must not have a void reason");
        }
        if (status == InvoiceStatus.VOIDED && voidReason == null) {
            throw new IllegalArgumentException("a voided invoice must have a void reason");
        }
    }

    private static List<InvoiceLine> validateAndOrderLines(List<InvoiceLine> candidateLines, Currency currency) {
        Objects.requireNonNull(candidateLines, "invoice lines must not be null");
        if (candidateLines.isEmpty()) {
            throw new IllegalArgumentException("an invoice must contain at least one line");
        }
        HashSet<Integer> lineNumbers = new HashSet<>();
        for (InvoiceLine line : candidateLines) {
            Objects.requireNonNull(line, "invoice line must not be null");
            if (!lineNumbers.add(line.lineNumber())) {
                throw new IllegalArgumentException("invoice line numbers must be unique");
            }
            if (!currency.equals(line.netAmount().currency())) {
                throw new IllegalArgumentException("invoice header and lines must use one currency");
            }
        }
        return candidateLines.stream()
                .sorted(Comparator.comparingInt(InvoiceLine::lineNumber))
                .toList();
    }

    private void ensureActive(String message) {
        if (status != InvoiceStatus.ACTIVE) {
            throw new IllegalStateException(message);
        }
    }

    private void validateChangeTime(Instant changedAt) {
        Objects.requireNonNull(changedAt, "changedAt must not be null");
        if (changedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("changedAt must not be before the current updatedAt");
        }
    }

    public InvoiceId id() { return id; }
    public SourceSystemCode sourceSystem() { return sourceSystem; }
    public SourceRecordId sourceRecordId() { return sourceRecordId; }
    public SupplierId supplierId() { return supplierId; }
    public DocumentNumber documentNumber() { return documentNumber; }
    public DocumentType documentType() { return documentType; }
    public LocalDate issueDate() { return issueDate; }
    public LocalDate postingDate() { return postingDate; }
    public ReportingPeriod reportingPeriod() { return reportingPeriod; }
    public Money declaredNetTotal() { return declaredNetTotal; }
    public Money declaredTaxTotal() { return declaredTaxTotal; }
    public Money declaredGrossTotal() { return declaredGrossTotal; }
    public Sha256Hash sourcePayloadHash() { return sourcePayloadHash; }
    public InvoiceStatus status() { return status; }
    public List<InvoiceLine> lines() { return lines; }
    public Optional<CorrectionReason> lastCorrectionReason() { return Optional.ofNullable(lastCorrectionReason); }
    public Optional<CorrectionReason> voidReason() { return Optional.ofNullable(voidReason); }
    public OptionalLong version() { return version == null ? OptionalLong.empty() : OptionalLong.of(version); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Currency currency() { return declaredNetTotal.currency(); }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Invoice invoice && id.equals(invoice.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Invoice[id=" + id + ", source=" + sourceSystem + ", document=" + documentNumber
                + ", status=" + status + "]";
    }
}
