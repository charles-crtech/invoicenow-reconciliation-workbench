package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import java.util.List;

public record JsonImportPlan(
        long sourceSizeBytes,
        Sha256Hash sourceSha256,
        long sourceUnitCount,
        List<CsvImportPlan.SupplierRow> suppliers,
        List<CsvImportPlan.InvoiceLineRow> invoiceLines,
        List<InvoiceSource> invoiceSources,
        List<CsvImportPlan.LedgerRow> ledgerEntries,
        List<CsvImportPlan.RejectedRow> rejectedRows) {

    public JsonImportPlan {
        suppliers = List.copyOf(suppliers);
        invoiceLines = List.copyOf(invoiceLines);
        invoiceSources = List.copyOf(invoiceSources);
        ledgerEntries = List.copyOf(ledgerEntries);
        rejectedRows = List.copyOf(rejectedRows);
    }

    public record InvoiceSource(
            String sourceSystem,
            String sourceRecordId,
            CsvImportPlan.SourceRecord source) {}
}
