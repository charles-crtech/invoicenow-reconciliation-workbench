package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.domain.CsvArtifactKind;
import io.github.charlescrtech.invoicenow.imports.domain.QuarantineReason;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CsvImportPlan(
        CsvArtifactKind kind,
        long sourceSizeBytes,
        Sha256Hash sourceSha256,
        long dataRecordCount,
        List<SupplierRow> suppliers,
        List<InvoiceLineRow> invoiceLines,
        List<LedgerRow> ledgerEntries,
        List<RejectedRow> rejectedRows) {

    public CsvImportPlan {
        suppliers = List.copyOf(suppliers);
        invoiceLines = List.copyOf(invoiceLines);
        ledgerEntries = List.copyOf(ledgerEntries);
        rejectedRows = List.copyOf(rejectedRows);
    }

    public record SourceRecord(long number, Sha256Hash hash, String original) {}

    public record RejectedRow(
            SourceRecord source,
            String recordType,
            QuarantineReason reason,
            String fieldName) {}

    public record SupplierRow(
            SourceRecord source,
            String supplierCode,
            String displayName,
            String registrationIdentifier,
            boolean gstRegistered,
            String status) {}

    public record InvoiceLineRow(
            SourceRecord source,
            String sourceSystem,
            String sourceRecordId,
            String supplierCode,
            String documentNumber,
            String documentType,
            LocalDate issueDate,
            LocalDate postingDate,
            LocalDate reportingPeriodStart,
            LocalDate reportingPeriodEnd,
            String currency,
            BigDecimal declaredNet,
            BigDecimal declaredTax,
            BigDecimal declaredGross,
            int lineNumber,
            String description,
            String itemCode,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal netAmount,
            String taxCategory,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal grossAmount,
            String lineCurrency) {}

    public record LedgerRow(
            SourceRecord source,
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
            BigDecimal taxAmount) {}
}
