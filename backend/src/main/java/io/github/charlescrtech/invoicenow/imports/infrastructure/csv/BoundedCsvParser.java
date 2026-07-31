package io.github.charlescrtech.invoicenow.imports.infrastructure.csv;

import io.github.charlescrtech.invoicenow.imports.application.CsvImportException;
import io.github.charlescrtech.invoicenow.imports.application.CsvImportPlan;
import io.github.charlescrtech.invoicenow.imports.domain.CsvArtifactKind;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.QuarantineReason;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class BoundedCsvParser {

    public static final int MAX_RECORD_BYTES = 65_536;
    public static final long MAX_DATA_RECORDS = 500_000;
    private static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]{1,4})?");
    private static final Pattern POSITIVE_INTEGER = Pattern.compile("[1-9][0-9]*");

    private static final List<String> SUPPLIER_HEADER = List.of(
            "contract_version", "dataset_id", "supplier_code", "display_name",
            "registration_identifier", "gst_registered", "status");
    private static final List<String> INVOICE_HEADER = List.of(
            "contract_version", "dataset_id", "source_system", "source_record_id",
            "supplier_code", "document_number", "document_type", "issue_date",
            "posting_date", "reporting_period_start", "reporting_period_end", "currency",
            "declared_net", "declared_tax", "declared_gross", "line_number", "description",
            "item_code", "quantity", "unit_price", "net_amount", "tax_category", "tax_rate",
            "tax_amount", "gross_amount", "line_currency");
    private static final List<String> LEDGER_HEADER = List.of(
            "contract_version", "dataset_id", "source_system", "source_record_id",
            "account_code", "counterparty_reference", "document_reference", "posting_date",
            "reporting_period_start", "reporting_period_end", "currency", "debit_amount",
            "credit_amount", "tax_amount");

    private final int maxRecordBytes;
    private final long maxDataRecords;

    public BoundedCsvParser() {
        this(MAX_RECORD_BYTES, MAX_DATA_RECORDS);
    }

    BoundedCsvParser(int maxRecordBytes, long maxDataRecords) {
        if (maxRecordBytes < 1 || maxDataRecords < 0) {
            throw new IllegalArgumentException("CSV parser bounds must not be negative");
        }
        this.maxRecordBytes = maxRecordBytes;
        this.maxDataRecords = maxDataRecords;
    }

    public CsvImportPlan parse(InputStream input, ImportBatch batch) {
        CsvArtifactKind kind;
        try {
            kind = CsvArtifactKind.fromFileName(batch.sourceName());
        } catch (IllegalArgumentException exception) {
            throw fatal("IMPORT_CSV_FILENAME", exception.getMessage());
        }

        List<CsvImportPlan.SupplierRow> suppliers = new ArrayList<>();
        List<CsvImportPlan.InvoiceLineRow> invoiceLines = new ArrayList<>();
        List<CsvImportPlan.LedgerRow> ledgerEntries = new ArrayList<>();
        List<CsvImportPlan.RejectedRow> rejected = new ArrayList<>();
        long[] recordNumber = {0};
        boolean[] headerSeen = {false};

        ReadSummary summary = readRecords(input, batch.sourceSizeBytes(), raw -> {
            if (!headerSeen[0]) {
                if (startsWithBom(raw)) {
                    throw fatal("IMPORT_CSV_BOM", "UTF-8 BOM is forbidden by source contract v1");
                }
                String decodedHeader = decode(raw);
                List<String> actualHeader = parseFields(decodedHeader);
                if (!actualHeader.equals(header(kind))
                        || !decodedHeader.equals(String.join(",", header(kind)))) {
                    throw fatal("CONTRACT_CSV_HEADER", "CSV header does not match source contract v1");
                }
                headerSeen[0] = true;
                return;
            }
            recordNumber[0]++;
            CsvImportPlan.SourceRecord source = new CsvImportPlan.SourceRecord(
                    recordNumber[0],
                    sha256(raw),
                    decode(raw));
            List<String> fields;
            try {
                fields = parseFields(source.original());
            } catch (CsvImportException exception) {
                rejected.add(new CsvImportPlan.RejectedRow(
                        source, kind.recordType(), QuarantineReason.CONTRACT_VALUE_INVALID, null));
                return;
            }
            if (fields.size() != header(kind).size()) {
                rejected.add(new CsvImportPlan.RejectedRow(
                        source, kind.recordType(), QuarantineReason.CONTRACT_CSV_FIELD_COUNT, null));
                return;
            }
            try {
                validateCorrelation(fields, batch);
                switch (kind) {
                    case SUPPLIERS -> suppliers.add(supplier(source, fields));
                    case INVOICES -> invoiceLines.add(invoice(source, fields));
                    case LEDGER_ENTRIES -> ledgerEntries.add(ledger(source, fields));
                }
            } catch (RowFailure failure) {
                rejected.add(new CsvImportPlan.RejectedRow(
                        source, kind.recordType(), failure.reason, failure.fieldName));
            }
        });
        if (!headerSeen[0]) {
            throw fatal("CONTRACT_CSV_HEADER", "CSV source has no header");
        }

        return new CsvImportPlan(
                kind,
                summary.totalSourceBytes(),
                summary.sourceSha256(),
                recordNumber[0],
                suppliers,
                invoiceLines,
                ledgerEntries,
                rejected);
    }

    private ReadSummary readRecords(
            InputStream input,
            long expectedBytes,
            Consumer<byte[]> recordConsumer) {
        try {
            MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream current = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            long records = 0;
            boolean inQuotes = false;
            int read;
            while ((read = input.read(buffer)) != -1) {
                for (int offset = 0; offset < read; offset++) {
                    byte value = buffer[offset];
                    total++;
                    if (total > ImportBatch.MAX_SOURCE_SIZE_BYTES || total > expectedBytes) {
                        throw nonTerminal("IMPORT_SOURCE_SIZE_MISMATCH", "uploaded CSV size differs from registration");
                    }
                    fileDigest.update(value);
                    if (value == '"') {
                        inQuotes = !inQuotes;
                    }
                    if (value == '\n' && !inQuotes) {
                        byte[] record = current.toByteArray();
                        if (record.length > 0 && record[record.length - 1] == '\r') {
                            record = java.util.Arrays.copyOf(record, record.length - 1);
                        }
                        records = addRecord(records, record, recordConsumer);
                        current.reset();
                    } else {
                        current.write(value);
                        if (current.size() > maxRecordBytes) {
                            throw fatal("IMPORT_CSV_RECORD_TOO_LARGE", "CSV logical record exceeds limit");
                        }
                    }
                }
            }
            if (total != expectedBytes) {
                throw nonTerminal("IMPORT_SOURCE_SIZE_MISMATCH", "uploaded CSV size differs from registration");
            }
            if (inQuotes) {
                throw fatal("IMPORT_CSV_SYNTAX", "CSV quoted field is not terminated");
            }
            if (current.size() > 0) {
                records = addRecord(records, current.toByteArray(), recordConsumer);
            }
            Sha256Hash sourceHash = new Sha256Hash(HexFormat.of().formatHex(fileDigest.digest()));
            return new ReadSummary(total, sourceHash, records);
        } catch (IOException exception) {
            throw fatal("IMPORT_CSV_READ_FAILED", "CSV stream could not be read");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private long addRecord(long records, byte[] record, Consumer<byte[]> consumer) {
        if (records >= maxDataRecords + 1) {
            throw fatal("IMPORT_CSV_TOO_MANY_RECORDS", "CSV data record count exceeds limit");
        }
        consumer.accept(record);
        return records + 1;
    }

    private static boolean startsWithBom(byte[] value) {
        return value.length >= 3
                && (value[0] & 0xff) == 0xef
                && (value[1] & 0xff) == 0xbb
                && (value[2] & 0xff) == 0xbf;
    }

    private static String decode(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw fatal("IMPORT_CSV_UTF8", "CSV contains malformed UTF-8");
        }
    }

    private static List<String> parseFields(String record) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        boolean atStart = true;
        for (int index = 0; index < record.length(); index++) {
            char character = record.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < record.length() && record.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        afterQuote = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (afterQuote) {
                if (character != ',') {
                    throw fatal("IMPORT_CSV_SYNTAX", "unexpected character after quoted CSV field");
                }
                fields.add(field.toString());
                field.setLength(0);
                afterQuote = false;
                atStart = true;
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
                atStart = true;
            } else if (character == '"') {
                if (!atStart) {
                    throw fatal("IMPORT_CSV_SYNTAX", "quote appears in an unquoted CSV field");
                }
                quoted = true;
                atStart = false;
            } else {
                if (character == '\r') {
                    throw fatal("IMPORT_CSV_SYNTAX", "standalone carriage return is not permitted");
                }
                field.append(character);
                atStart = false;
            }
        }
        if (quoted) {
            throw fatal("IMPORT_CSV_SYNTAX", "quoted CSV field is not terminated");
        }
        fields.add(field.toString());
        return fields;
    }

    private static void validateCorrelation(List<String> fields, ImportBatch batch) {
        if (!"1.0".equals(fields.get(0).strip())) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "contract_version");
        }
        if (!batch.datasetId().equals(fields.get(1).strip())) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "dataset_id");
        }
    }

    private static CsvImportPlan.SupplierRow supplier(CsvImportPlan.SourceRecord source, List<String> fields) {
        String registration = required(fields, 4, "registration_identifier");
        if (!registration.startsWith("SYNTH-")) {
            throw row(QuarantineReason.CONTRACT_SYNTHETIC_ID_REQUIRED, "registration_identifier");
        }
        String booleanText = required(fields, 5, "gst_registered");
        if (!booleanText.equals("true") && !booleanText.equals("false")) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "gst_registered");
        }
        String status = upper(fields, 6, "status");
        if (!List.of("ACTIVE", "INACTIVE", "ARCHIVED").contains(status)) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "status");
        }
        return new CsvImportPlan.SupplierRow(
                source,
                upper(fields, 2, "supplier_code"),
                required(fields, 3, "display_name"),
                registration,
                Boolean.parseBoolean(booleanText),
                status);
    }

    private static CsvImportPlan.InvoiceLineRow invoice(
            CsvImportPlan.SourceRecord source,
            List<String> fields) {
        String currency = upper(fields, 11, "currency");
        String lineCurrency = upper(fields, 25, "line_currency");
        if (!currency.equals(lineCurrency)) {
            throw row(QuarantineReason.CONTRACT_CURRENCY_MISMATCH, "line_currency");
        }
        String documentType = upper(fields, 6, "document_type");
        if (!List.of("INVOICE", "CREDIT_NOTE", "DEBIT_NOTE").contains(documentType)) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "document_type");
        }
        String taxCategory = upper(fields, 21, "tax_category");
        if (!List.of("STANDARD_RATED", "ZERO_RATED", "EXEMPT", "OUT_OF_SCOPE").contains(taxCategory)) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "tax_category");
        }
        return new CsvImportPlan.InvoiceLineRow(
                source,
                upper(fields, 2, "source_system"),
                required(fields, 3, "source_record_id"),
                upper(fields, 4, "supplier_code"),
                upper(fields, 5, "document_number"),
                documentType,
                date(fields, 7, "issue_date"),
                date(fields, 8, "posting_date"),
                date(fields, 9, "reporting_period_start"),
                date(fields, 10, "reporting_period_end"),
                currency,
                decimal(fields, 12, "declared_net"),
                decimal(fields, 13, "declared_tax"),
                decimal(fields, 14, "declared_gross"),
                positiveInteger(fields, 15, "line_number"),
                required(fields, 16, "description"),
                optionalUpper(fields.get(17)),
                decimal(fields, 18, "quantity"),
                decimal(fields, 19, "unit_price"),
                decimal(fields, 20, "net_amount"),
                taxCategory,
                decimal(fields, 22, "tax_rate"),
                decimal(fields, 23, "tax_amount"),
                decimal(fields, 24, "gross_amount"),
                lineCurrency);
    }

    private static CsvImportPlan.LedgerRow ledger(CsvImportPlan.SourceRecord source, List<String> fields) {
        BigDecimal debit = decimal(fields, 11, "debit_amount");
        BigDecimal credit = decimal(fields, 12, "credit_amount");
        if ((debit.signum() > 0) == (credit.signum() > 0)
                || debit.signum() < 0
                || credit.signum() < 0) {
            throw row(QuarantineReason.CONTRACT_LEDGER_SIDE, "debit_amount");
        }
        return new CsvImportPlan.LedgerRow(
                source,
                upper(fields, 2, "source_system"),
                required(fields, 3, "source_record_id"),
                upper(fields, 4, "account_code"),
                upper(fields, 5, "counterparty_reference"),
                upper(fields, 6, "document_reference"),
                date(fields, 7, "posting_date"),
                date(fields, 8, "reporting_period_start"),
                date(fields, 9, "reporting_period_end"),
                upper(fields, 10, "currency"),
                debit,
                credit,
                decimal(fields, 13, "tax_amount"));
    }

    private static String required(List<String> fields, int index, String name) {
        String value = fields.get(index).strip();
        if (value.isBlank()) {
            throw row(QuarantineReason.CONTRACT_REQUIRED_FIELD, name);
        }
        return value;
    }

    private static String upper(List<String> fields, int index, String name) {
        return required(fields, index, name).toUpperCase(Locale.ROOT);
    }

    private static String optionalUpper(String value) {
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped.toUpperCase(Locale.ROOT);
    }

    private static BigDecimal decimal(List<String> fields, int index, String name) {
        String value = required(fields, index, name);
        if (!DECIMAL.matcher(value).matches()) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, name);
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, name);
        }
    }

    private static int positiveInteger(List<String> fields, int index, String name) {
        String value = required(fields, index, name);
        if (!POSITIVE_INTEGER.matcher(value).matches()) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, name);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, name);
        }
    }

    private static LocalDate date(List<String> fields, int index, String name) {
        String value = required(fields, index, name);
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, name);
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, name);
        }
    }

    private static List<String> header(CsvArtifactKind kind) {
        return switch (kind) {
            case SUPPLIERS -> SUPPLIER_HEADER;
            case INVOICES -> INVOICE_HEADER;
            case LEDGER_ENTRIES -> LEDGER_HEADER;
        };
    }

    private static Sha256Hash sha256(byte[] value) {
        try {
            return new Sha256Hash(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private static RowFailure row(QuarantineReason reason, String fieldName) {
        return new RowFailure(reason, fieldName);
    }

    private static CsvImportException fatal(String code, String message) {
        return new CsvImportException(code, true, message);
    }

    private static CsvImportException nonTerminal(String code, String message) {
        return new CsvImportException(code, false, message);
    }

    private record ReadSummary(long totalSourceBytes, Sha256Hash sourceSha256, long records) {}

    private static final class RowFailure extends RuntimeException {
        private final QuarantineReason reason;
        private final String fieldName;

        private RowFailure(QuarantineReason reason, String fieldName) {
            this.reason = reason;
            this.fieldName = fieldName;
        }
    }
}
