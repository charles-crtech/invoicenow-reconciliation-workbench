package io.github.charlescrtech.invoicenow.generator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Deterministic source-contract v1 generator; it contains no wall-clock or random-UUID input. */
public final class SyntheticDatasetGenerator {

    public static final String GENERATOR_VERSION = "1.0.0";
    public static final RoundingMode FIXTURE_TAX_ROUNDING = RoundingMode.HALF_UP;

    private static final String CONTRACT_VERSION = "1.0";
    private static final String INVOICE_HEADER = String.join(",",
            "contract_version", "dataset_id", "source_system", "source_record_id", "supplier_code",
            "document_number", "document_type", "issue_date", "posting_date", "reporting_period_start",
            "reporting_period_end", "currency", "declared_net", "declared_tax", "declared_gross",
            "line_number", "description", "item_code", "quantity", "unit_price", "net_amount",
            "tax_category", "tax_rate", "tax_amount", "gross_amount", "line_currency");
    private static final String SUPPLIER_HEADER = String.join(",",
            "contract_version", "dataset_id", "supplier_code", "display_name",
            "registration_identifier", "gst_registered", "status");
    private static final String LEDGER_HEADER = String.join(",",
            "contract_version", "dataset_id", "source_system", "source_record_id", "account_code",
            "counterparty_reference", "document_reference", "posting_date", "reporting_period_start",
            "reporting_period_end", "currency", "debit_amount", "credit_amount", "tax_amount");
    private static final ObjectMapper JSON = new ObjectMapper();

    public GeneratedDataset generate(GeneratorProfile profile) {
        Objects.requireNonNull(profile, "generator profile must not be null");
        DatasetModel model = buildModel(profile);
        DatasetSummary summary = summarize(profile, model);
        return new GeneratedDataset(
                profile,
                summary,
                List.of(
                        GeneratedArtifact.utf8("dataset.json", renderJson(profile, model)),
                        GeneratedArtifact.utf8("suppliers.csv", renderSuppliersCsv(profile, model.suppliers())),
                        GeneratedArtifact.utf8("invoices.csv", renderInvoicesCsv(profile, model.invoices())),
                        GeneratedArtifact.utf8(
                                "ledger_entries.csv",
                                renderLedgerCsv(profile, model.ledgerEntries()))));
    }

    private static DatasetModel buildModel(GeneratorProfile profile) {
        Random random = new Random(profile.seed());
        List<SupplierRow> suppliers = new ArrayList<>(profile.supplierCount());
        for (int index = 1; index <= profile.supplierCount(); index++) {
            suppliers.add(new SupplierRow(
                    formatted("SUP-%03d", index),
                    formatted("Synthetic Supplier %03d", index),
                    formatted("SYNTH-UEN-%06d", index),
                    index % 4 != 0,
                    "ACTIVE"));
        }

        List<InvoiceRow> invoices = new ArrayList<>(profile.invoiceCount());
        List<LedgerRow> ledgerEntries = new ArrayList<>(profile.invoiceCount());
        long periodDays = ChronoUnit.DAYS.between(
                profile.reportingPeriodStart(),
                profile.reportingPeriodEnd());
        int latestIssueOffset = Math.toIntExact(periodDays - 3);
        int scale = profile.monetaryScale();
        BigDecimal zero = BigDecimal.ZERO.setScale(scale);

        for (int invoiceIndex = 1; invoiceIndex <= profile.invoiceCount(); invoiceIndex++) {
            SupplierRow supplier = suppliers.get((invoiceIndex - 1) % suppliers.size());
            LocalDate issueDate = profile.reportingPeriodStart().plusDays(random.nextInt(latestIssueOffset + 1));
            LocalDate postingDate = issueDate.plusDays(random.nextInt(3));
            int lineCount = 1 + random.nextInt(profile.maxLinesPerInvoice());
            List<InvoiceLineRow> lines = new ArrayList<>(lineCount);
            BigDecimal netTotal = zero;
            BigDecimal taxTotal = zero;
            BigDecimal grossTotal = zero;

            for (int lineNumber = 1; lineNumber <= lineCount; lineNumber++) {
                BigDecimal quantity = BigDecimal.valueOf(1 + random.nextInt(5)).setScale(4);
                BigDecimal unitPrice = BigDecimal.valueOf(1_000L + random.nextInt(49_001), 2);
                BigDecimal netAmount = unitPrice.multiply(quantity).setScale(scale, RoundingMode.UNNECESSARY);
                BigDecimal taxAmount = netAmount
                        .multiply(profile.taxRate())
                        .setScale(scale, FIXTURE_TAX_ROUNDING);
                BigDecimal grossAmount = netAmount.add(taxAmount);
                lines.add(new InvoiceLineRow(
                        lineNumber,
                        formatted("Synthetic service, invoice %06d line %02d", invoiceIndex, lineNumber),
                        formatted("ITEM-%03d", lineNumber),
                        quantity,
                        unitPrice,
                        netAmount,
                        "STANDARD_RATED",
                        profile.taxRate(),
                        taxAmount,
                        grossAmount,
                        profile.currency().getCurrencyCode()));
                netTotal = netTotal.add(netAmount);
                taxTotal = taxTotal.add(taxAmount);
                grossTotal = grossTotal.add(grossAmount);
            }

            String documentNumber = formatted("INV-202607-%06d", invoiceIndex);
            InvoiceRow invoice = new InvoiceRow(
                    "ERP_ONE",
                    formatted("invoice-%06d", invoiceIndex),
                    supplier.supplierCode(),
                    documentNumber,
                    "INVOICE",
                    issueDate,
                    postingDate,
                    profile.reportingPeriodStart(),
                    profile.reportingPeriodEnd(),
                    profile.currency().getCurrencyCode(),
                    netTotal,
                    taxTotal,
                    grossTotal,
                    List.copyOf(lines));
            invoices.add(invoice);
            ledgerEntries.add(new LedgerRow(
                    "LEDGER_ONE",
                    formatted("ledger-%06d", invoiceIndex),
                    "EXP.5000",
                    supplier.supplierCode(),
                    documentNumber,
                    postingDate,
                    profile.reportingPeriodStart(),
                    profile.reportingPeriodEnd(),
                    profile.currency().getCurrencyCode(),
                    grossTotal,
                    zero,
                    taxTotal));
        }
        return new DatasetModel(List.copyOf(suppliers), List.copyOf(invoices), List.copyOf(ledgerEntries));
    }

    private static DatasetSummary summarize(GeneratorProfile profile, DatasetModel model) {
        BigDecimal zero = BigDecimal.ZERO.setScale(profile.monetaryScale());
        BigDecimal invoiceNet = zero;
        BigDecimal invoiceTax = zero;
        BigDecimal invoiceGross = zero;
        BigDecimal ledgerDebit = zero;
        BigDecimal ledgerCredit = zero;
        int lines = 0;
        for (InvoiceRow invoice : model.invoices()) {
            invoiceNet = invoiceNet.add(invoice.declaredNet());
            invoiceTax = invoiceTax.add(invoice.declaredTax());
            invoiceGross = invoiceGross.add(invoice.declaredGross());
            lines += invoice.lines().size();
        }
        for (LedgerRow entry : model.ledgerEntries()) {
            ledgerDebit = ledgerDebit.add(entry.debitAmount());
            ledgerCredit = ledgerCredit.add(entry.creditAmount());
        }
        return new DatasetSummary(
                model.suppliers().size(),
                model.invoices().size(),
                lines,
                model.ledgerEntries().size(),
                invoiceNet,
                invoiceTax,
                invoiceGross,
                ledgerDebit,
                ledgerCredit);
    }

    private static String renderJson(GeneratorProfile profile, DatasetModel model) {
        ObjectNode root = JSON.createObjectNode();
        root.put("contract_version", CONTRACT_VERSION);
        root.put("dataset_id", profile.datasetId());
        ArrayNode suppliers = root.putArray("suppliers");
        for (SupplierRow supplier : model.suppliers()) {
            ObjectNode node = suppliers.addObject();
            node.put("supplier_code", supplier.supplierCode());
            node.put("display_name", supplier.displayName());
            node.put("registration_identifier", supplier.registrationIdentifier());
            node.put("gst_registered", supplier.gstRegistered());
            node.put("status", supplier.status());
        }
        ArrayNode invoices = root.putArray("invoices");
        for (InvoiceRow invoice : model.invoices()) {
            ObjectNode node = invoices.addObject();
            addInvoiceHeader(node, invoice);
            ArrayNode lines = node.putArray("lines");
            for (InvoiceLineRow line : invoice.lines()) {
                ObjectNode lineNode = lines.addObject();
                lineNode.put("line_number", line.lineNumber());
                lineNode.put("description", line.description());
                lineNode.put("item_code", line.itemCode());
                lineNode.put("quantity", line.quantity());
                lineNode.put("unit_price", line.unitPrice());
                lineNode.put("net_amount", line.netAmount());
                lineNode.put("tax_category", line.taxCategory());
                lineNode.put("tax_rate", line.taxRate());
                lineNode.put("tax_amount", line.taxAmount());
                lineNode.put("gross_amount", line.grossAmount());
                lineNode.put("currency", line.currency());
            }
        }
        ArrayNode ledgerEntries = root.putArray("ledger_entries");
        for (LedgerRow entry : model.ledgerEntries()) {
            ObjectNode node = ledgerEntries.addObject();
            node.put("source_system", entry.sourceSystem());
            node.put("source_record_id", entry.sourceRecordId());
            node.put("account_code", entry.accountCode());
            node.put("counterparty_reference", entry.counterpartyReference());
            node.put("document_reference", entry.documentReference());
            node.put("posting_date", entry.postingDate().toString());
            node.put("reporting_period_start", entry.reportingPeriodStart().toString());
            node.put("reporting_period_end", entry.reportingPeriodEnd().toString());
            node.put("currency", entry.currency());
            node.put("debit_amount", entry.debitAmount());
            node.put("credit_amount", entry.creditAmount());
            node.put("tax_amount", entry.taxAmount());
        }
        return JSON.writeValueAsString(root) + "\n";
    }

    private static void addInvoiceHeader(ObjectNode node, InvoiceRow invoice) {
        node.put("source_system", invoice.sourceSystem());
        node.put("source_record_id", invoice.sourceRecordId());
        node.put("supplier_code", invoice.supplierCode());
        node.put("document_number", invoice.documentNumber());
        node.put("document_type", invoice.documentType());
        node.put("issue_date", invoice.issueDate().toString());
        node.put("posting_date", invoice.postingDate().toString());
        node.put("reporting_period_start", invoice.reportingPeriodStart().toString());
        node.put("reporting_period_end", invoice.reportingPeriodEnd().toString());
        node.put("currency", invoice.currency());
        node.put("declared_net", invoice.declaredNet());
        node.put("declared_tax", invoice.declaredTax());
        node.put("declared_gross", invoice.declaredGross());
    }

    private static String renderSuppliersCsv(GeneratorProfile profile, List<SupplierRow> suppliers) {
        StringBuilder csv = new StringBuilder(SUPPLIER_HEADER).append('\n');
        for (SupplierRow supplier : suppliers) {
            appendCsvRow(csv, List.of(
                    CONTRACT_VERSION,
                    profile.datasetId(),
                    supplier.supplierCode(),
                    supplier.displayName(),
                    supplier.registrationIdentifier(),
                    Boolean.toString(supplier.gstRegistered()),
                    supplier.status()));
        }
        return csv.toString();
    }

    private static String renderInvoicesCsv(GeneratorProfile profile, List<InvoiceRow> invoices) {
        StringBuilder csv = new StringBuilder(INVOICE_HEADER).append('\n');
        for (InvoiceRow invoice : invoices) {
            for (InvoiceLineRow line : invoice.lines()) {
                appendCsvRow(csv, List.of(
                        CONTRACT_VERSION,
                        profile.datasetId(),
                        invoice.sourceSystem(),
                        invoice.sourceRecordId(),
                        invoice.supplierCode(),
                        invoice.documentNumber(),
                        invoice.documentType(),
                        invoice.issueDate().toString(),
                        invoice.postingDate().toString(),
                        invoice.reportingPeriodStart().toString(),
                        invoice.reportingPeriodEnd().toString(),
                        invoice.currency(),
                        decimal(invoice.declaredNet()),
                        decimal(invoice.declaredTax()),
                        decimal(invoice.declaredGross()),
                        Integer.toString(line.lineNumber()),
                        line.description(),
                        line.itemCode(),
                        decimal(line.quantity()),
                        decimal(line.unitPrice()),
                        decimal(line.netAmount()),
                        line.taxCategory(),
                        decimal(line.taxRate()),
                        decimal(line.taxAmount()),
                        decimal(line.grossAmount()),
                        line.currency()));
            }
        }
        return csv.toString();
    }

    private static String renderLedgerCsv(GeneratorProfile profile, List<LedgerRow> entries) {
        StringBuilder csv = new StringBuilder(LEDGER_HEADER).append('\n');
        for (LedgerRow entry : entries) {
            appendCsvRow(csv, List.of(
                    CONTRACT_VERSION,
                    profile.datasetId(),
                    entry.sourceSystem(),
                    entry.sourceRecordId(),
                    entry.accountCode(),
                    entry.counterpartyReference(),
                    entry.documentReference(),
                    entry.postingDate().toString(),
                    entry.reportingPeriodStart().toString(),
                    entry.reportingPeriodEnd().toString(),
                    entry.currency(),
                    decimal(entry.debitAmount()),
                    decimal(entry.creditAmount()),
                    decimal(entry.taxAmount())));
        }
        return csv.toString();
    }

    private static void appendCsvRow(StringBuilder csv, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(escapeCsv(values.get(index)));
        }
        csv.append('\n');
    }

    private static String escapeCsv(String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String decimal(BigDecimal value) {
        return value.toPlainString();
    }

    private static String formatted(String format, Object... arguments) {
        return String.format(Locale.ROOT, format, arguments);
    }

    private record DatasetModel(
            List<SupplierRow> suppliers,
            List<InvoiceRow> invoices,
            List<LedgerRow> ledgerEntries) {
    }

    private record SupplierRow(
            String supplierCode,
            String displayName,
            String registrationIdentifier,
            boolean gstRegistered,
            String status) {
    }

    private record InvoiceRow(
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
            List<InvoiceLineRow> lines) {
    }

    private record InvoiceLineRow(
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
            String currency) {
    }

    private record LedgerRow(
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
            BigDecimal taxAmount) {
    }
}
