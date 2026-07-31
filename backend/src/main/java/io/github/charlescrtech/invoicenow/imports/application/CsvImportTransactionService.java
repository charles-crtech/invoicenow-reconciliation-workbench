package io.github.charlescrtech.invoicenow.imports.application;

import io.github.charlescrtech.invoicenow.imports.domain.CsvArtifactKind;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchStatus;
import io.github.charlescrtech.invoicenow.imports.domain.QuarantineReason;
import io.github.charlescrtech.invoicenow.imports.domain.QuarantineRecord;
import io.github.charlescrtech.invoicenow.invoices.application.InvoiceImportService;
import io.github.charlescrtech.invoicenow.invoices.domain.DocumentNumber;
import io.github.charlescrtech.invoicenow.invoices.domain.DocumentType;
import io.github.charlescrtech.invoicenow.invoices.domain.Invoice;
import io.github.charlescrtech.invoicenow.invoices.domain.InvoiceLine;
import io.github.charlescrtech.invoicenow.invoices.domain.TaxCategory;
import io.github.charlescrtech.invoicenow.reconciliation.application.LedgerEntryImportService;
import io.github.charlescrtech.invoicenow.reconciliation.domain.AccountCode;
import io.github.charlescrtech.invoicenow.reconciliation.domain.CounterpartyReference;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerDocumentReference;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerEntry;
import io.github.charlescrtech.invoicenow.shared.domain.money.Money;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import io.github.charlescrtech.invoicenow.shared.domain.time.ReportingPeriod;
import io.github.charlescrtech.invoicenow.suppliers.application.SupplierImportService;
import io.github.charlescrtech.invoicenow.suppliers.domain.RegistrationIdentifier;
import io.github.charlescrtech.invoicenow.suppliers.domain.Supplier;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierCode;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierName;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CsvImportTransactionService {

    private final ImportBatchRepository batches;
    private final QuarantineRepository quarantine;
    private final SupplierImportService suppliers;
    private final InvoiceImportService invoices;
    private final LedgerEntryImportService ledgerEntries;
    private final Clock clock;

    CsvImportTransactionService(
            ImportBatchRepository batches,
            QuarantineRepository quarantine,
            SupplierImportService suppliers,
            InvoiceImportService invoices,
            LedgerEntryImportService ledgerEntries,
            Clock clock) {
        this.batches = batches;
        this.quarantine = quarantine;
        this.suppliers = suppliers;
        this.invoices = invoices;
        this.ledgerEntries = ledgerEntries;
        this.clock = clock;
    }

    @Transactional
    public ImportBatch commit(ImportBatchId batchId, CsvImportPlan plan) {
        ImportBatch registered = batches.findById(batchId)
                .orElseThrow(ImportBatchNotFoundException::new);
        if (registered.status() != ImportBatchStatus.REGISTERED) {
            throw new CsvImportException(
                    "IMPORT_BATCH_STATE_CONFLICT", false, "import batch is not registered");
        }
        Instant now = clock.instant();
        ImportBatch processing = batches.update(registered.start(now))
                .orElseThrow(CsvImportTransactionService::concurrentChange);

        List<QuarantineRecord> quarantined = new ArrayList<>();
        for (CsvImportPlan.RejectedRow row : plan.rejectedRows()) {
            quarantined.add(quarantine(
                    processing, row.source(), row.recordType(), row.reason(), row.fieldName(), now));
        }

        long accepted = switch (plan.kind()) {
            case SUPPLIERS -> importSuppliers(processing, plan.suppliers(), quarantined, now);
            case INVOICES -> importInvoices(processing, plan.invoiceLines(), quarantined, now);
            case LEDGER_ENTRIES -> importLedger(processing, plan.ledgerEntries(), quarantined, now);
        };
        quarantine.saveAll(quarantined);
        ImportBatch completed = processing.complete(accepted, 0, quarantined.size(), clock.instant());
        return batches.update(completed).orElseThrow(CsvImportTransactionService::concurrentChange);
    }

    private long importSuppliers(
            ImportBatch batch,
            List<CsvImportPlan.SupplierRow> rows,
            List<QuarantineRecord> quarantined,
            Instant now) {
        Set<String> codes = new HashSet<>();
        Set<String> registrations = new HashSet<>();
        long accepted = 0;
        for (CsvImportPlan.SupplierRow row : rows) {
            try {
                if (!codes.add(row.supplierCode()) || !registrations.add(row.registrationIdentifier())) {
                    throw new RowMappingFailure(QuarantineReason.CONTRACT_DUPLICATE_IDENTITY, null);
                }
                SupplierCode code = SupplierCode.of(row.supplierCode());
                if (suppliers.findByCode(code).isPresent()) {
                    throw new RowMappingFailure(QuarantineReason.CONTRACT_DUPLICATE_IDENTITY, "supplier_code");
                }
                Supplier supplier = Supplier.create(
                        code,
                        SupplierName.of(row.displayName()),
                        RegistrationIdentifier.of(row.registrationIdentifier()),
                        row.gstRegistered(),
                        now);
                supplier = switch (row.status()) {
                    case "ACTIVE" -> supplier;
                    case "INACTIVE" -> supplier.deactivate(now);
                    case "ARCHIVED" -> supplier.archive(now);
                    default -> throw new RowMappingFailure(QuarantineReason.CONTRACT_VALUE_INVALID, "status");
                };
                suppliers.save(supplier);
                accepted++;
            } catch (RowMappingFailure failure) {
                quarantined.add(quarantine(
                        batch, row.source(), CsvArtifactKind.SUPPLIERS.recordType(),
                        failure.reason, failure.fieldName, now));
            } catch (IllegalArgumentException failure) {
                quarantined.add(quarantine(
                        batch, row.source(), CsvArtifactKind.SUPPLIERS.recordType(),
                        QuarantineReason.CONTRACT_VALUE_INVALID, null, now));
            }
        }
        return accepted;
    }

    private long importInvoices(
            ImportBatch batch,
            List<CsvImportPlan.InvoiceLineRow> rows,
            List<QuarantineRecord> quarantined,
            Instant now) {
        Map<InvoiceKey, List<CsvImportPlan.InvoiceLineRow>> groups = new LinkedHashMap<>();
        for (CsvImportPlan.InvoiceLineRow row : rows) {
            groups.computeIfAbsent(new InvoiceKey(row.sourceSystem(), row.sourceRecordId()), ignored -> new ArrayList<>())
                    .add(row);
        }
        long accepted = 0;
        Set<String> documentIdentities = new HashSet<>();
        for (List<CsvImportPlan.InvoiceLineRow> group : groups.values()) {
            CsvImportPlan.InvoiceLineRow header = group.getFirst();
            try {
                requireMatchingHeaders(group, header);
                String documentIdentity = header.sourceSystem() + "|" + header.supplierCode()
                        + "|" + header.documentType() + "|" + header.documentNumber();
                if (!documentIdentities.add(documentIdentity)) {
                    throw new RowMappingFailure(QuarantineReason.CONTRACT_DUPLICATE_IDENTITY, null);
                }
                SourceSystemCode sourceSystem = SourceSystemCode.of(header.sourceSystem());
                SourceRecordId sourceRecordId = SourceRecordId.of(header.sourceRecordId());
                if (invoices.findBySourceIdentity(sourceSystem, sourceRecordId).isPresent()) {
                    throw new RowMappingFailure(QuarantineReason.CONTRACT_DUPLICATE_IDENTITY, null);
                }
                Supplier supplier = suppliers.findByCode(SupplierCode.of(header.supplierCode()))
                        .orElseThrow(() -> new RowMappingFailure(
                                QuarantineReason.CONTRACT_SUPPLIER_REFERENCE, "supplier_code"));
                Set<Integer> lineNumbers = new HashSet<>();
                List<InvoiceLine> lines = group.stream().map(row -> {
                    if (!lineNumbers.add(row.lineNumber())) {
                        throw new RowMappingFailure(QuarantineReason.CONTRACT_DUPLICATE_IDENTITY, "line_number");
                    }
                    return new InvoiceLine(
                            row.lineNumber(),
                            row.description(),
                            row.itemCode(),
                            row.quantity(),
                            Money.of(row.unitPrice(), row.currency()),
                            Money.of(row.netAmount(), row.currency()),
                            TaxCategory.valueOf(row.taxCategory()),
                            row.taxRate(),
                            Money.of(row.taxAmount(), row.currency()),
                            Money.of(row.grossAmount(), row.currency()));
                }).toList();
                Invoice invoice = Invoice.create(
                        sourceSystem,
                        sourceRecordId,
                        supplier.id(),
                        DocumentNumber.of(header.documentNumber()),
                        DocumentType.valueOf(header.documentType()),
                        header.issueDate(),
                        header.postingDate(),
                        new ReportingPeriod(header.reportingPeriodStart(), header.reportingPeriodEnd()),
                        Money.of(header.declaredNet(), header.currency()),
                        Money.of(header.declaredTax(), header.currency()),
                        Money.of(header.declaredGross(), header.currency()),
                        groupHash(group),
                        lines,
                        now);
                invoices.save(invoice);
                accepted += group.size() + 1L;
            } catch (RowMappingFailure failure) {
                quarantineInvoiceGroup(batch, group, quarantined, failure.reason, failure.fieldName, now);
            } catch (IllegalArgumentException failure) {
                quarantineInvoiceGroup(
                        batch, group, quarantined, QuarantineReason.CONTRACT_VALUE_INVALID, null, now);
            }
        }
        return accepted;
    }

    private long importLedger(
            ImportBatch batch,
            List<CsvImportPlan.LedgerRow> rows,
            List<QuarantineRecord> quarantined,
            Instant now) {
        Set<InvoiceKey> identities = new HashSet<>();
        long accepted = 0;
        for (CsvImportPlan.LedgerRow row : rows) {
            try {
                InvoiceKey key = new InvoiceKey(row.sourceSystem(), row.sourceRecordId());
                SourceSystemCode sourceSystem = SourceSystemCode.of(row.sourceSystem());
                SourceRecordId sourceRecordId = SourceRecordId.of(row.sourceRecordId());
                if (!identities.add(key) || ledgerEntries.findBySourceIdentity(sourceSystem, sourceRecordId).isPresent()) {
                    throw new RowMappingFailure(QuarantineReason.CONTRACT_DUPLICATE_IDENTITY, null);
                }
                LedgerEntry entry = LedgerEntry.create(
                        sourceSystem,
                        sourceRecordId,
                        AccountCode.of(row.accountCode()),
                        CounterpartyReference.of(row.counterpartyReference()),
                        LedgerDocumentReference.of(row.documentReference()),
                        row.postingDate(),
                        new ReportingPeriod(row.reportingPeriodStart(), row.reportingPeriodEnd()),
                        Money.of(row.debitAmount(), row.currency()),
                        Money.of(row.creditAmount(), row.currency()),
                        Money.of(row.taxAmount(), row.currency()),
                        row.source().hash(),
                        now);
                ledgerEntries.save(entry);
                accepted++;
            } catch (RowMappingFailure failure) {
                quarantined.add(quarantine(
                        batch, row.source(), CsvArtifactKind.LEDGER_ENTRIES.recordType(),
                        failure.reason, failure.fieldName, now));
            } catch (IllegalArgumentException failure) {
                quarantined.add(quarantine(
                        batch, row.source(), CsvArtifactKind.LEDGER_ENTRIES.recordType(),
                        QuarantineReason.CONTRACT_VALUE_INVALID, null, now));
            }
        }
        return accepted;
    }

    private static void requireMatchingHeaders(
            List<CsvImportPlan.InvoiceLineRow> group,
            CsvImportPlan.InvoiceLineRow header) {
        for (CsvImportPlan.InvoiceLineRow row : group) {
            boolean matches = row.supplierCode().equals(header.supplierCode())
                    && row.documentNumber().equals(header.documentNumber())
                    && row.documentType().equals(header.documentType())
                    && row.issueDate().equals(header.issueDate())
                    && row.postingDate().equals(header.postingDate())
                    && row.reportingPeriodStart().equals(header.reportingPeriodStart())
                    && row.reportingPeriodEnd().equals(header.reportingPeriodEnd())
                    && row.currency().equals(header.currency())
                    && row.declaredNet().compareTo(header.declaredNet()) == 0
                    && row.declaredTax().compareTo(header.declaredTax()) == 0
                    && row.declaredGross().compareTo(header.declaredGross()) == 0;
            if (!matches) {
                throw new RowMappingFailure(QuarantineReason.CONTRACT_INVOICE_HEADER_MISMATCH, null);
            }
        }
    }

    private static Sha256Hash groupHash(List<CsvImportPlan.InvoiceLineRow> group) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CsvImportPlan.InvoiceLineRow row : group) {
                byte[] bytes = row.source().original().getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
                digest.update(bytes);
            }
            return new Sha256Hash(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private static void quarantineInvoiceGroup(
            ImportBatch batch,
            List<CsvImportPlan.InvoiceLineRow> group,
            List<QuarantineRecord> quarantined,
            QuarantineReason reason,
            String fieldName,
            Instant now) {
        for (CsvImportPlan.InvoiceLineRow row : group) {
            quarantined.add(quarantine(batch, row.source(), "INVOICE_LINE", reason, fieldName, now));
        }
        CsvImportPlan.SourceRecord first = group.getFirst().source();
        quarantined.add(quarantine(batch, first, "INVOICE", reason, fieldName, now));
    }

    private static QuarantineRecord quarantine(
            ImportBatch batch,
            CsvImportPlan.SourceRecord source,
            String recordType,
            QuarantineReason reason,
            String fieldName,
            Instant now) {
        return new QuarantineRecord(
                UUID.randomUUID(),
                batch.id(),
                batch.sourceName(),
                source.number(),
                recordType,
                source.hash(),
                source.original(),
                reason,
                fieldName,
                now);
    }

    private static CsvImportException concurrentChange() {
        return new CsvImportException(
                "IMPORT_BATCH_STATE_CONFLICT", false, "import batch changed concurrently");
    }

    private record InvoiceKey(String sourceSystem, String sourceRecordId) {}

    private static final class RowMappingFailure extends RuntimeException {
        private final QuarantineReason reason;
        private final String fieldName;

        private RowMappingFailure(QuarantineReason reason, String fieldName) {
            this.reason = reason;
            this.fieldName = fieldName;
        }
    }
}
