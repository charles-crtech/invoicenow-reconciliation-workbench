package io.github.charlescrtech.invoicenow.imports.infrastructure.json;

import io.github.charlescrtech.invoicenow.imports.application.CsvImportPlan;
import io.github.charlescrtech.invoicenow.imports.application.JsonImportException;
import io.github.charlescrtech.invoicenow.imports.application.JsonImportPlan;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.QuarantineReason;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class BoundedJsonParser {

    public static final int MAX_LOGICAL_RECORD_BYTES = 65_536;
    public static final long MAX_SOURCE_UNITS = 500_000;
    public static final int MAX_DEPTH = 8;
    public static final int MAX_STRING_LENGTH = 4_096;
    public static final int MAX_NUMBER_LENGTH = 64;
    public static final long MAX_TOKENS = 20_000_000;

    private static final BigDecimal MAX_DECIMAL = new BigDecimal("999999999999999.9999");
    private static final Pattern DATASET_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{2,63}");
    private static final Pattern SUPPLIER_CODE = Pattern.compile("[A-Z0-9][A-Z0-9_-]{2,31}");
    private static final Pattern REGISTRATION = Pattern.compile("SYNTH-[A-Z0-9][A-Z0-9-]{2,57}");
    private static final Pattern SOURCE_SYSTEM = Pattern.compile("[A-Z][A-Z0-9_]{2,31}");
    private static final Pattern SOURCE_RECORD_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}");
    private static final Pattern REFERENCE = Pattern.compile("[A-Z0-9][A-Z0-9._/-]{0,63}");
    private static final Pattern ACCOUNT_CODE = Pattern.compile("[A-Z0-9][A-Z0-9._-]{2,31}");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern SAFE_FIELD = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    private static final List<String> TOP_FIELDS = List.of(
            "contract_version", "dataset_id", "suppliers", "invoices", "ledger_entries");
    private static final List<String> SUPPLIER_FIELDS = List.of(
            "supplier_code", "display_name", "registration_identifier", "gst_registered", "status");
    private static final List<String> INVOICE_FIELDS = List.of(
            "source_system", "source_record_id", "supplier_code", "document_number", "document_type",
            "issue_date", "posting_date", "reporting_period_start", "reporting_period_end", "currency",
            "declared_net", "declared_tax", "declared_gross", "lines");
    private static final List<String> LINE_FIELDS = List.of(
            "line_number", "description", "item_code", "quantity", "unit_price", "net_amount",
            "tax_category", "tax_rate", "tax_amount", "gross_amount", "currency");
    private static final List<String> LEDGER_FIELDS = List.of(
            "source_system", "source_record_id", "account_code", "counterparty_reference",
            "document_reference", "posting_date", "reporting_period_start", "reporting_period_end",
            "currency", "debit_amount", "credit_amount", "tax_amount");

    private final ObjectMapper json;
    private final int maxLogicalRecordBytes;
    private final long maxSourceUnits;

    public BoundedJsonParser() {
        this(MAX_LOGICAL_RECORD_BYTES, MAX_SOURCE_UNITS);
    }

    BoundedJsonParser(int maxLogicalRecordBytes, long maxSourceUnits) {
        if (maxLogicalRecordBytes < 1 || maxSourceUnits < 0) {
            throw new IllegalArgumentException("JSON parser bounds must not be negative");
        }
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxNestingDepth(MAX_DEPTH)
                .maxDocumentLength(ImportBatch.MAX_SOURCE_SIZE_BYTES)
                .maxTokenCount(MAX_TOKENS)
                .maxNumberLength(MAX_NUMBER_LENGTH)
                .maxStringLength(MAX_STRING_LENGTH)
                .maxNameLength(64)
                .build();
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(constraints)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.json = JsonMapper.builder(factory)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        this.maxLogicalRecordBytes = maxLogicalRecordBytes;
        this.maxSourceUnits = maxSourceUnits;
    }

    public JsonImportPlan parse(InputStream input, ImportBatch batch) {
        ParseContext context = new ParseContext(batch);
        BoundedDigestInputStream bounded = new BoundedDigestInputStream(input, batch.sourceSizeBytes());
        try {
            PushbackInputStream source = new PushbackInputStream(bounded, 3);
            byte[] prefix = source.readNBytes(3);
            if (isBom(prefix)) {
                throw fatal("IMPORT_JSON_BOM", "UTF-8 BOM is forbidden by source contract v1");
            }
            source.unread(prefix);
            try (JsonParser parser = json.createParser(source)) {
                parseEnvelope(parser, context);
            }
        } catch (JsonImportException exception) {
            throw exception;
        } catch (StreamConstraintsException exception) {
            throw fatal("IMPORT_JSON_LIMIT_EXCEEDED", "JSON parser safety limit exceeded");
        } catch (JacksonException exception) {
            if (bounded.exceeded()) {
                throw nonTerminal("IMPORT_SOURCE_SIZE_MISMATCH", "uploaded JSON size differs from registration");
            }
            throw fatal("IMPORT_JSON_SYNTAX", "JSON syntax or UTF-8 is invalid: " + exception.getMessage());
        } catch (IOException exception) {
            if (bounded.exceeded()) {
                throw nonTerminal("IMPORT_SOURCE_SIZE_MISMATCH", "uploaded JSON size differs from registration");
            }
            throw fatal("IMPORT_JSON_READ_FAILED", "JSON stream could not be read");
        }
        if (bounded.count() != batch.sourceSizeBytes()) {
            throw nonTerminal("IMPORT_SOURCE_SIZE_MISMATCH", "uploaded JSON size differs from registration");
        }
        return new JsonImportPlan(
                bounded.count(),
                bounded.sha256(),
                context.sourceUnits,
                context.suppliers,
                context.invoiceLines,
                context.invoiceSources,
                context.ledgerEntries,
                context.rejected);
    }

    private void parseEnvelope(JsonParser parser, ParseContext context) throws JacksonException {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw fatal("IMPORT_JSON_STRUCTURE", "JSON dataset must be an object");
        }
        Set<String> seen = new HashSet<>();
        String contractVersion = null;
        String datasetId = null;
        long supplierItems = 0;
        boolean topLevelUnknown = false;
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
            if (token != JsonToken.PROPERTY_NAME) {
                throw fatal("IMPORT_JSON_STRUCTURE", "JSON dataset property is invalid");
            }
            String name = parser.currentName();
            if (!seen.add(name)) {
                throw fatal("IMPORT_JSON_DUPLICATE_PROPERTY", "JSON contains a duplicate property");
            }
            JsonToken valueToken = parser.nextToken();
            if (valueToken == null) {
                throw fatal("IMPORT_JSON_SYNTAX", "JSON dataset ended unexpectedly");
            }
            switch (name) {
                case "contract_version" -> contractVersion = scalarText(parser, "contract_version");
                case "dataset_id" -> datasetId = scalarText(parser, "dataset_id");
                case "suppliers" -> supplierItems = parseArray(parser, context, RecordKind.SUPPLIER);
                case "invoices" -> parseArray(parser, context, RecordKind.INVOICE);
                case "ledger_entries" -> parseArray(parser, context, RecordKind.LEDGER);
                default -> {
                    JsonNode value = json.readTree(parser);
                    ObjectNode evidence = json.createObjectNode();
                    evidence.set(name, value);
                    CsvImportPlan.SourceRecord source = context.source(evidence, List.of(name));
                    context.rejected.add(new CsvImportPlan.RejectedRow(
                            source,
                            "DATASET",
                            QuarantineReason.CONTRACT_UNKNOWN_FIELD,
                            safeField(name)));
                    topLevelUnknown = true;
                }
            }
        }
        if (parser.nextToken() != null) {
            throw fatal("IMPORT_JSON_TRAILING_CONTENT", "JSON contains trailing content");
        }
        for (String required : TOP_FIELDS) {
            if (!seen.contains(required)) {
                throw fatal("CONTRACT_REQUIRED_FIELD", "JSON dataset is missing a required property");
            }
        }
        if (!"1.0".equals(contractVersion)
                || !DATASET_ID.matcher(datasetId == null ? "" : datasetId.strip()).matches()
                || !context.batch.datasetId().equals(datasetId == null ? null : datasetId.strip())) {
            throw fatal("CONTRACT_VALUE_INVALID", "JSON dataset correlation is invalid");
        }
        if (supplierItems == 0 && !topLevelUnknown) {
            throw fatal("CONTRACT_REQUIRED_FIELD", "JSON dataset requires at least one supplier");
        }
    }

    private long parseArray(JsonParser parser, ParseContext context, RecordKind kind) throws JacksonException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw fatal("IMPORT_JSON_STRUCTURE", "JSON dataset collection must be an array");
        }
        long items = 0;
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) {
                throw fatal("IMPORT_JSON_SYNTAX", "JSON collection ended unexpectedly");
            }
            JsonNode node = json.readTree(parser);
            items++;
            switch (kind) {
                case SUPPLIER -> parseSupplier(node, context);
                case INVOICE -> parseInvoice(node, context);
                case LEDGER -> parseLedger(node, context);
            }
        }
        return items;
    }

    private void parseSupplier(JsonNode node, ParseContext context) {
        CsvImportPlan.SourceRecord source = context.source(node, SUPPLIER_FIELDS);
        try {
            requireObject(node);
            rejectUnknown(node, SUPPLIER_FIELDS);
            String registration = text(node, "registration_identifier", 61, null);
            if (!REGISTRATION.matcher(registration).matches()) {
                throw row(QuarantineReason.CONTRACT_SYNTHETIC_ID_REQUIRED, "registration_identifier");
            }
            JsonNode gst = required(node, "gst_registered");
            if (!gst.isBoolean()) {
                throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "gst_registered");
            }
            String status = upperText(node, "status", 16, null);
            if (!Set.of("ACTIVE", "INACTIVE", "ARCHIVED").contains(status)) {
                throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "status");
            }
            String supplierCode = upperText(node, "supplier_code", 32, SUPPLIER_CODE);
            if (!context.supplierCodes.add(supplierCode)
                    || !context.supplierRegistrations.add(registration)) {
                throw row(QuarantineReason.CONTRACT_DUPLICATE_IDENTITY, null);
            }
            context.suppliers.add(new CsvImportPlan.SupplierRow(
                    source,
                    supplierCode,
                    text(node, "display_name", 200, null),
                    registration,
                    gst.booleanValue(),
                    status));
        } catch (RowFailure failure) {
            context.reject(source, "SUPPLIER", failure);
        }
    }

    private void parseInvoice(JsonNode node, ParseContext context) {
        CsvImportPlan.SourceRecord invoiceSource = context.source(node, INVOICE_FIELDS);
        List<CsvImportPlan.SourceRecord> lineSources = new ArrayList<>();
        JsonNode linesNode = node.isObject() ? node.get("lines") : null;
        if (linesNode != null && linesNode.isArray()) {
            for (JsonNode line : linesNode) {
                lineSources.add(context.source(line, LINE_FIELDS));
            }
        }
        try {
            requireObject(node);
            rejectUnknown(node, INVOICE_FIELDS);
            JsonNode lines = required(node, "lines");
            if (!lines.isArray() || lines.isEmpty()) {
                throw row(QuarantineReason.CONTRACT_REQUIRED_FIELD, "lines");
            }
            String sourceSystem = upperText(node, "source_system", 32, SOURCE_SYSTEM);
            String sourceRecordId = text(node, "source_record_id", 100, SOURCE_RECORD_ID);
            String supplierCode = upperText(node, "supplier_code", 32, SUPPLIER_CODE);
            String documentNumber = upperText(node, "document_number", 64, REFERENCE);
            String documentType = upperText(node, "document_type", 16, null);
            if (!Set.of("INVOICE", "CREDIT_NOTE", "DEBIT_NOTE").contains(documentType)) {
                throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "document_type");
            }
            LocalDate issueDate = date(node, "issue_date");
            LocalDate postingDate = date(node, "posting_date");
            LocalDate periodStart = date(node, "reporting_period_start");
            LocalDate periodEnd = date(node, "reporting_period_end");
            if (postingDate.isBefore(issueDate) || !periodStart.isBefore(periodEnd)) {
                throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "posting_date");
            }
            String currency = upperText(node, "currency", 3, CURRENCY);
            BigDecimal declaredNet = decimal(node, "declared_net", true);
            BigDecimal declaredTax = decimal(node, "declared_tax", true);
            BigDecimal declaredGross = decimal(node, "declared_gross", true);
            String sourceIdentity = sourceSystem + "\u0000" + sourceRecordId;
            String documentIdentity = sourceSystem + "\u0000" + supplierCode
                    + "\u0000" + documentType + "\u0000" + documentNumber;
            if (!context.invoiceSourceIdentities.add(sourceIdentity)
                    || !context.invoiceDocumentIdentities.add(documentIdentity)) {
                throw row(QuarantineReason.CONTRACT_DUPLICATE_IDENTITY, null);
            }

            List<CsvImportPlan.InvoiceLineRow> parsed = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                JsonNode line = lines.get(index);
                requireObject(line);
                rejectUnknown(line, LINE_FIELDS);
                String lineCurrency = upperText(line, "currency", 3, CURRENCY);
                if (!currency.equals(lineCurrency)) {
                    throw row(QuarantineReason.CONTRACT_CURRENCY_MISMATCH, "currency");
                }
                String taxCategory = upperText(line, "tax_category", 32, null);
                if (!Set.of("STANDARD_RATED", "ZERO_RATED", "EXEMPT", "OUT_OF_SCOPE")
                        .contains(taxCategory)) {
                    throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "tax_category");
                }
                BigDecimal quantity = decimal(line, "quantity", false);
                if (quantity.signum() <= 0) {
                    throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "quantity");
                }
                BigDecimal taxRate = decimal(line, "tax_rate", false);
                if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) {
                    throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "tax_rate");
                }
                parsed.add(new CsvImportPlan.InvoiceLineRow(
                        lineSources.get(index),
                        sourceSystem,
                        sourceRecordId,
                        supplierCode,
                        documentNumber,
                        documentType,
                        issueDate,
                        postingDate,
                        periodStart,
                        periodEnd,
                        currency,
                        declaredNet,
                        declaredTax,
                        declaredGross,
                        positiveInteger(line, "line_number"),
                        text(line, "description", 500, null),
                        optionalUpperText(line, "item_code", 64, REFERENCE),
                        quantity,
                        decimal(line, "unit_price", true),
                        decimal(line, "net_amount", true),
                        taxCategory,
                        taxRate,
                        decimal(line, "tax_amount", true),
                        decimal(line, "gross_amount", true),
                        lineCurrency));
            }
            context.invoiceSources.add(new JsonImportPlan.InvoiceSource(
                    sourceSystem, sourceRecordId, invoiceSource));
            context.invoiceLines.addAll(parsed);
        } catch (RowFailure failure) {
            context.reject(invoiceSource, "INVOICE", failure);
            for (CsvImportPlan.SourceRecord lineSource : lineSources) {
                context.reject(lineSource, "INVOICE_LINE", failure);
            }
        }
    }

    private void parseLedger(JsonNode node, ParseContext context) {
        CsvImportPlan.SourceRecord source = context.source(node, LEDGER_FIELDS);
        try {
            requireObject(node);
            rejectUnknown(node, LEDGER_FIELDS);
            BigDecimal debit = decimal(node, "debit_amount", false);
            BigDecimal credit = decimal(node, "credit_amount", false);
            if ((debit.signum() > 0) == (credit.signum() > 0)) {
                throw row(QuarantineReason.CONTRACT_LEDGER_SIDE, "debit_amount");
            }
            BigDecimal tax = decimal(node, "tax_amount", false);
            if (tax.signum() < 0) {
                throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "tax_amount");
            }
            LocalDate periodStart = date(node, "reporting_period_start");
            LocalDate periodEnd = date(node, "reporting_period_end");
            if (!periodStart.isBefore(periodEnd)) {
                throw row(QuarantineReason.CONTRACT_VALUE_INVALID, "reporting_period_start");
            }
            String sourceSystem = upperText(node, "source_system", 32, SOURCE_SYSTEM);
            String sourceRecordId = text(node, "source_record_id", 100, SOURCE_RECORD_ID);
            if (!context.ledgerIdentities.add(sourceSystem + "\u0000" + sourceRecordId)) {
                throw row(QuarantineReason.CONTRACT_DUPLICATE_IDENTITY, null);
            }
            context.ledgerEntries.add(new CsvImportPlan.LedgerRow(
                    source,
                    sourceSystem,
                    sourceRecordId,
                    upperText(node, "account_code", 32, ACCOUNT_CODE),
                    upperText(node, "counterparty_reference", 64, REFERENCE),
                    upperText(node, "document_reference", 64, REFERENCE),
                    date(node, "posting_date"),
                    periodStart,
                    periodEnd,
                    upperText(node, "currency", 3, CURRENCY),
                    debit,
                    credit,
                    tax));
        } catch (RowFailure failure) {
            context.reject(source, "LEDGER_ENTRY", failure);
        }
    }

    private static void requireObject(JsonNode node) {
        if (!node.isObject()) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, null);
        }
    }

    private static void rejectUnknown(JsonNode node, List<String> allowed) {
        node.propertyNames().stream()
                .filter(name -> !allowed.contains(name))
                .sorted()
                .findFirst()
                .ifPresent(name -> {
                    throw row(QuarantineReason.CONTRACT_UNKNOWN_FIELD, safeField(name));
                });
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw row(QuarantineReason.CONTRACT_REQUIRED_FIELD, field);
        }
        return value;
    }

    private static String text(JsonNode node, String field, int maxCodePoints, Pattern pattern) {
        JsonNode value = required(node, field);
        if (!value.isString()) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, field);
        }
        String normalized = value.stringValue().strip();
        if (normalized.isBlank()) {
            throw row(QuarantineReason.CONTRACT_REQUIRED_FIELD, field);
        }
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints
                || (pattern != null && !pattern.matcher(normalized).matches())) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, field);
        }
        return normalized;
    }

    private static String upperText(JsonNode node, String field, int maxCodePoints, Pattern pattern) {
        String normalized = text(node, field, maxCodePoints, null).toUpperCase(Locale.ROOT);
        if (pattern != null && !pattern.matcher(normalized).matches()) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, field);
        }
        return normalized;
    }

    private static String optionalUpperText(JsonNode node, String field, int maxCodePoints, Pattern pattern) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, field);
        }
        String normalized = value.stringValue().strip();
        if (normalized.isEmpty()) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints
                || (pattern != null && !pattern.matcher(normalized).matches())) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, field);
        }
        return normalized;
    }

    private static BigDecimal decimal(JsonNode node, String field, boolean signed) {
        JsonNode value = required(node, field);
        if (!value.isNumber()) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, field);
        }
        BigDecimal decimal = value.decimalValue();
        if (decimal.scale() > 4
                || decimal.abs().compareTo(MAX_DECIMAL) > 0
                || (!signed && decimal.signum() < 0)) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, field);
        }
        return decimal;
    }

    private static int positiveInteger(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, field);
        }
        return value.intValue();
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field, 10, DATE);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw row(QuarantineReason.CONTRACT_VALUE_INVALID, field);
        }
    }

    private static String scalarText(JsonParser parser, String field) throws JacksonException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw fatal("CONTRACT_VALUE_INVALID", "JSON envelope scalar is invalid");
        }
        String value = parser.getString().strip();
        if (value.isBlank()) {
            throw fatal("CONTRACT_REQUIRED_FIELD", "JSON envelope scalar is blank");
        }
        return value;
    }

    private JsonNode ordered(JsonNode node, List<String> order) {
        if (!node.isObject()) {
            return node;
        }
        ObjectNode ordered = json.createObjectNode();
        for (String name : order) {
            if (node.has(name)) {
                JsonNode value = node.get(name);
                if (name.equals("lines") && value.isArray()) {
                    ArrayNode lines = json.createArrayNode();
                    for (JsonNode line : value) {
                        lines.add(ordered(line, LINE_FIELDS));
                    }
                    ordered.set(name, lines);
                } else {
                    ordered.set(name, canonicalValue(value));
                }
            }
        }
        node.propertyNames().stream()
                .filter(name -> !order.contains(name))
                .sorted(Comparator.naturalOrder())
                .forEach(name -> ordered.set(name, canonicalValue(node.get(name))));
        return ordered;
    }

    private JsonNode canonicalValue(JsonNode node) {
        if (node.isObject()) {
            ObjectNode ordered = json.createObjectNode();
            node.propertyNames().stream()
                    .sorted(Comparator.naturalOrder())
                    .forEach(name -> ordered.set(name, canonicalValue(node.get(name))));
            return ordered;
        }
        if (node.isArray()) {
            ArrayNode ordered = json.createArrayNode();
            node.forEach(value -> ordered.add(canonicalValue(value)));
            return ordered;
        }
        return node;
    }

    private CsvImportPlan.SourceRecord source(long number, JsonNode node, List<String> order) {
        try {
            byte[] canonical = json.writeValueAsBytes(ordered(node, order));
            if (canonical.length > maxLogicalRecordBytes) {
                throw fatal("IMPORT_JSON_RECORD_TOO_LARGE", "JSON logical record exceeds limit");
            }
            return new CsvImportPlan.SourceRecord(
                    number,
                    sha256(canonical),
                    new String(canonical, java.nio.charset.StandardCharsets.UTF_8));
        } catch (JacksonException exception) {
            throw fatal("IMPORT_JSON_CANONICALIZATION", "JSON logical record could not be canonicalized");
        }
    }

    private static boolean isBom(byte[] prefix) {
        return prefix.length >= 3
                && (prefix[0] & 0xff) == 0xef
                && (prefix[1] & 0xff) == 0xbb
                && (prefix[2] & 0xff) == 0xbf;
    }

    private static Sha256Hash sha256(byte[] value) {
        try {
            return new Sha256Hash(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private static String safeField(String field) {
        return field != null && SAFE_FIELD.matcher(field).matches() ? field : null;
    }

    private static RowFailure row(QuarantineReason reason, String fieldName) {
        return new RowFailure(reason, safeField(fieldName));
    }

    private static JsonImportException fatal(String code, String message) {
        return new JsonImportException(code, true, message);
    }

    private static JsonImportException nonTerminal(String code, String message) {
        return new JsonImportException(code, false, message);
    }

    private enum RecordKind {
        SUPPLIER,
        INVOICE,
        LEDGER
    }

    private final class ParseContext {
        private final ImportBatch batch;
        private final List<CsvImportPlan.SupplierRow> suppliers = new ArrayList<>();
        private final List<CsvImportPlan.InvoiceLineRow> invoiceLines = new ArrayList<>();
        private final List<JsonImportPlan.InvoiceSource> invoiceSources = new ArrayList<>();
        private final List<CsvImportPlan.LedgerRow> ledgerEntries = new ArrayList<>();
        private final List<CsvImportPlan.RejectedRow> rejected = new ArrayList<>();
        private final Set<String> supplierCodes = new HashSet<>();
        private final Set<String> supplierRegistrations = new HashSet<>();
        private final Set<String> invoiceSourceIdentities = new HashSet<>();
        private final Set<String> invoiceDocumentIdentities = new HashSet<>();
        private final Set<String> ledgerIdentities = new HashSet<>();
        private long sourceUnits;

        private ParseContext(ImportBatch batch) {
            this.batch = batch;
        }

        private CsvImportPlan.SourceRecord source(JsonNode node, List<String> order) {
            if (sourceUnits >= maxSourceUnits) {
                throw fatal("IMPORT_JSON_TOO_MANY_RECORDS", "JSON source-unit count exceeds limit");
            }
            sourceUnits++;
            return BoundedJsonParser.this.source(sourceUnits, node, order);
        }

        private void reject(CsvImportPlan.SourceRecord source, String recordType, RowFailure failure) {
            rejected.add(new CsvImportPlan.RejectedRow(
                    source, recordType, failure.reason, failure.fieldName));
        }
    }

    private static final class RowFailure extends RuntimeException {
        private final QuarantineReason reason;
        private final String fieldName;

        private RowFailure(QuarantineReason reason, String fieldName) {
            this.reason = reason;
            this.fieldName = fieldName;
        }
    }

    private static final class BoundedDigestInputStream extends FilterInputStream {
        private final long expectedBytes;
        private final MessageDigest digest;
        private long count;
        private boolean exceeded;

        private BoundedDigestInputStream(InputStream input, long expectedBytes) {
            super(input);
            this.expectedBytes = expectedBytes;
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 must be available", exception);
            }
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                digest.update((byte) value);
                add(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                digest.update(buffer, offset, read);
                add(read);
            }
            return read;
        }

        private void add(int read) throws IOException {
            count += read;
            if (count > expectedBytes || count > ImportBatch.MAX_SOURCE_SIZE_BYTES) {
                exceeded = true;
                throw new IOException("registered JSON byte bound exceeded");
            }
        }

        private long count() {
            return count;
        }

        private boolean exceeded() {
            return exceeded;
        }

        private Sha256Hash sha256() {
            return new Sha256Hash(HexFormat.of().formatHex(digest.digest()));
        }
    }
}
