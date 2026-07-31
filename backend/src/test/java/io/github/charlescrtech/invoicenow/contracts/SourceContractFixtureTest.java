package io.github.charlescrtech.invoicenow.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SourceContractFixtureTest {

    private static final Path CONTRACT_ROOT = Path.of("..", "contracts", "source", "v1")
            .toAbsolutePath()
            .normalize();
    private static final Path VALID_ROOT = CONTRACT_ROOT.resolve("fixtures/valid");
    private static final Path INVALID_ROOT = CONTRACT_ROOT.resolve("fixtures/invalid");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void allJsonArtifactsParseAndSchemasDeclareClosedVersionedContracts() throws IOException {
        try (Stream<Path> artifacts = Files.walk(CONTRACT_ROOT)) {
            List<Path> jsonArtifacts = artifacts
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList();
            assertThat(jsonArtifacts).hasSize(7);
            for (Path artifact : jsonArtifacts) {
                assertThat(JSON.readTree(Files.readString(artifact, StandardCharsets.UTF_8)))
                        .as("parse %s", CONTRACT_ROOT.relativize(artifact))
                        .isNotNull();
            }
        }

        JsonNode datasetSchema = json("schemas/dataset.schema.json");
        JsonNode csvSchema = json("schemas/csv-bundle.schema.json");

        assertThat(datasetSchema.path("$schema").asText())
                .isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(csvSchema.path("$schema").asText())
                .isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(datasetSchema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(csvSchema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(datasetSchema.path("properties").path("contract_version").path("const").asText())
                .isEqualTo("1.0");
        assertThat(csvSchema.path("properties").path("contract_version").path("const").asText())
                .isEqualTo("1.0");
    }

    @Test
    void csvMetadataDefinesExactUniqueBundleAndColumnOrder() throws IOException {
        JsonNode metadata = json("csv-contract.json");

        assertThat(metadata.path("contract_version").asText()).isEqualTo("1.0");
        assertThat(metadata.path("encoding").asText()).isEqualTo("UTF-8");
        assertThat(metadata.path("byte_order_mark").asBoolean()).isFalse();
        assertThat(metadata.path("delimiter").asText()).isEqualTo(",");
        assertThat(metadata.path("quote").asText()).isEqualTo("\"");
        assertThat(fileNames(metadata)).containsExactly(
                "suppliers.csv", "invoices.csv", "ledger_entries.csv");

        for (JsonNode file : metadata.path("files")) {
            List<String> columns = columnNames(file);
            assertThat(columns).doesNotHaveDuplicates();
            assertThat(columns).startsWith("contract_version", "dataset_id");
            assertThat(columns).allMatch(name -> name.matches("[a-z][a-z0-9_]*"));
        }
        assertThat(columnsFor(metadata, "invoices.csv")).endsWith("gross_amount", "line_currency");
    }

    @Test
    void validJsonAndCsvFixturesAreEquivalentAndCrossReferencesResolve() throws IOException {
        JsonNode dataset = JSON.readTree(Files.readString(VALID_ROOT.resolve("dataset.json")));
        JsonNode metadata = json("csv-contract.json");
        CsvTable suppliers = csv(VALID_ROOT.resolve("suppliers.csv"));
        CsvTable invoices = csv(VALID_ROOT.resolve("invoices.csv"));
        CsvTable ledger = csv(VALID_ROOT.resolve("ledger_entries.csv"));

        assertTableContract(suppliers, columnsFor(metadata, "suppliers.csv"));
        assertTableContract(invoices, columnsFor(metadata, "invoices.csv"));
        assertTableContract(ledger, columnsFor(metadata, "ledger_entries.csv"));
        assertThat(dataset.path("contract_version").asText()).isEqualTo("1.0");
        assertThat(dataset.path("dataset_id").asText()).isEqualTo("contract-smoke-001");
        assertThat(dataset.path("suppliers")).hasSize(1);
        assertThat(dataset.path("invoices")).hasSize(1);
        assertThat(dataset.path("ledger_entries")).hasSize(1);

        Map<String, String> supplierRow = suppliers.rows().getFirst();
        JsonNode supplierJson = dataset.path("suppliers").get(0);
        assertCommonIdentity(supplierRow, dataset);
        assertThat(supplierRow.get("supplier_code")).isEqualTo(supplierJson.path("supplier_code").asText());
        assertThat(supplierRow.get("display_name")).isEqualTo(supplierJson.path("display_name").asText());
        assertThat(supplierRow.get("registration_identifier"))
                .isEqualTo(supplierJson.path("registration_identifier").asText())
                .startsWith("SYNTH-");
        assertThat(Boolean.parseBoolean(supplierRow.get("gst_registered")))
                .isEqualTo(supplierJson.path("gst_registered").asBoolean());

        Map<String, String> invoiceRow = invoices.rows().getFirst();
        JsonNode invoiceJson = dataset.path("invoices").get(0);
        JsonNode lineJson = invoiceJson.path("lines").get(0);
        assertCommonIdentity(invoiceRow, dataset);
        assertThat(invoiceRow.get("source_record_id")).isEqualTo(invoiceJson.path("source_record_id").asText());
        assertThat(invoiceRow.get("supplier_code")).isEqualTo(invoiceJson.path("supplier_code").asText());
        assertThat(invoiceRow.get("document_number")).isEqualTo(invoiceJson.path("document_number").asText());
        assertDecimal(invoiceRow, "declared_gross", invoiceJson);
        assertThat(Integer.parseInt(invoiceRow.get("line_number")))
                .isEqualTo(lineJson.path("line_number").asInt());
        assertThat(invoiceRow.get("description")).isEqualTo(lineJson.path("description").asText());
        assertDecimal(invoiceRow, "gross_amount", lineJson);
        assertThat(invoiceRow.get("currency"))
                .isEqualTo(invoiceRow.get("line_currency"))
                .isEqualTo(invoiceJson.path("currency").asText())
                .isEqualTo(lineJson.path("currency").asText());
        assertThat(LocalDate.parse(invoiceRow.get("posting_date")))
                .isBefore(LocalDate.parse(invoiceRow.get("reporting_period_end")));

        Map<String, String> ledgerRow = ledger.rows().getFirst();
        JsonNode ledgerJson = dataset.path("ledger_entries").get(0);
        assertCommonIdentity(ledgerRow, dataset);
        assertThat(ledgerRow.get("source_record_id")).isEqualTo(ledgerJson.path("source_record_id").asText());
        assertThat(ledgerRow.get("account_code")).isEqualTo(ledgerJson.path("account_code").asText());
        assertDecimal(ledgerRow, "debit_amount", ledgerJson);
        assertDecimal(ledgerRow, "credit_amount", ledgerJson);
        assertThat(isExactlyOnePositive(
                        decimal(ledgerRow, "debit_amount"),
                        decimal(ledgerRow, "credit_amount")))
                .isTrue();

        Set<String> supplierCodes = new HashSet<>();
        dataset.path("suppliers").forEach(node -> supplierCodes.add(node.path("supplier_code").asText()));
        assertThat(supplierCodes).contains(invoiceJson.path("supplier_code").asText());
        assertThat(suppliers.rows()).extracting(row -> row.get("supplier_code"))
                .contains(invoiceRow.get("supplier_code"));
    }

    @Test
    void everyInvalidFixtureProducesItsIndexedStableReasonCode() throws IOException {
        JsonNode index = JSON.readTree(Files.readString(INVALID_ROOT.resolve("cases.json")));
        Set<String> ids = new HashSet<>();
        Set<String> expectedCodes = Set.of(
                "CONTRACT_UNKNOWN_FIELD",
                "CONTRACT_CSV_HEADER",
                "CONTRACT_SYNTHETIC_ID_REQUIRED",
                "CONTRACT_LEDGER_SIDE",
                "CONTRACT_CURRENCY_MISMATCH");

        assertThat(index.path("contract_version").asText()).isEqualTo("1.0");
        assertThat(index.path("cases")).hasSize(5);
        for (JsonNode invalidCase : index.path("cases")) {
            String id = invalidCase.path("id").asText();
            String expected = invalidCase.path("expected_reason_code").asText();
            Path artifact = INVALID_ROOT.resolve(invalidCase.path("path").asText());
            assertThat(ids.add(id)).as("unique invalid fixture ID %s", id).isTrue();
            assertThat(expectedCodes).contains(expected);
            assertThat(Files.isRegularFile(artifact)).isTrue();

            String actual = invalidCase.path("format").asText().equals("json")
                    ? validateInvalidJson(JSON.readTree(Files.readString(artifact)))
                    : validateInvalidCsv(artifact, json("csv-contract.json"));
            assertThat(actual).as("reason for %s", id).isEqualTo(expected);
        }
    }

    private static String validateInvalidJson(JsonNode dataset) {
        Set<String> allowedRoot = Set.of(
                "contract_version", "dataset_id", "suppliers", "invoices", "ledger_entries");
        Set<String> actualRoot = new HashSet<>(dataset.propertyNames());
        if (!allowedRoot.containsAll(actualRoot)) {
            return "CONTRACT_UNKNOWN_FIELD";
        }
        for (JsonNode invoice : dataset.path("invoices")) {
            String currency = invoice.path("currency").asText();
            for (JsonNode line : invoice.path("lines")) {
                if (!currency.equals(line.path("currency").asText())) {
                    return "CONTRACT_CURRENCY_MISMATCH";
                }
            }
        }
        throw new AssertionError("indexed JSON fixture was not invalid");
    }

    private static String validateInvalidCsv(Path artifact, JsonNode metadata) throws IOException {
        CsvTable table = csv(artifact);
        String name = artifact.getFileName().toString();
        String contractFile = name.startsWith("suppliers")
                ? "suppliers.csv"
                : name.startsWith("invoices") ? "invoices.csv" : "ledger_entries.csv";
        if (!table.header().equals(columnsFor(metadata, contractFile))) {
            return "CONTRACT_CSV_HEADER";
        }
        if (contractFile.equals("suppliers.csv")
                && table.rows().stream()
                        .anyMatch(row -> !row.get("registration_identifier").startsWith("SYNTH-"))) {
            return "CONTRACT_SYNTHETIC_ID_REQUIRED";
        }
        if (contractFile.equals("ledger_entries.csv")) {
            Map<String, String> row = table.rows().getFirst();
            if (!isExactlyOnePositive(decimal(row, "debit_amount"), decimal(row, "credit_amount"))) {
                return "CONTRACT_LEDGER_SIDE";
            }
        }
        throw new AssertionError("indexed CSV fixture was not invalid");
    }

    private static boolean isExactlyOnePositive(BigDecimal debit, BigDecimal credit) {
        return (debit.signum() > 0 && credit.signum() == 0)
                || (credit.signum() > 0 && debit.signum() == 0);
    }

    private static void assertCommonIdentity(Map<String, String> row, JsonNode dataset) {
        assertThat(row.get("contract_version")).isEqualTo(dataset.path("contract_version").asText());
        assertThat(row.get("dataset_id")).isEqualTo(dataset.path("dataset_id").asText());
    }

    private static void assertDecimal(Map<String, String> row, String field, JsonNode json) {
        assertThat(decimal(row, field)).isEqualByComparingTo(json.path(field).decimalValue());
    }

    private static BigDecimal decimal(Map<String, String> row, String field) {
        return new BigDecimal(row.get(field));
    }

    private static void assertTableContract(CsvTable table, List<String> expectedHeader) {
        assertThat(table.header()).containsExactlyElementsOf(expectedHeader);
        assertThat(table.rows()).isNotEmpty();
        assertThat(table.rows()).allSatisfy(row -> {
            assertThat(row).hasSize(expectedHeader.size());
            assertThat(row.get("contract_version")).isEqualTo("1.0");
            assertThat(row.get("dataset_id")).isEqualTo("contract-smoke-001");
        });
    }

    private static JsonNode json(String relativePath) throws IOException {
        return JSON.readTree(Files.readString(CONTRACT_ROOT.resolve(relativePath), StandardCharsets.UTF_8));
    }

    private static List<String> fileNames(JsonNode metadata) {
        List<String> names = new ArrayList<>();
        metadata.path("files").forEach(file -> names.add(file.path("name").asText()));
        return names;
    }

    private static List<String> columnsFor(JsonNode metadata, String fileName) {
        for (JsonNode file : metadata.path("files")) {
            if (fileName.equals(file.path("name").asText())) {
                return columnNames(file);
            }
        }
        throw new IllegalArgumentException("unknown contract file " + fileName);
    }

    private static List<String> columnNames(JsonNode file) {
        List<String> names = new ArrayList<>();
        file.path("columns").forEach(column -> names.add(column.path("name").asText()));
        return names;
    }

    private static CsvTable csv(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        assertThat(bytes).as("non-empty CSV %s", path).isNotEmpty();
        boolean hasUtf8Bom = bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF;
        assertThat(hasUtf8Bom).as("no UTF-8 BOM in %s", path).isFalse();
        List<List<String>> records = parseCsv(new String(bytes, StandardCharsets.UTF_8));
        List<String> header = records.getFirst();
        List<Map<String, String>> rows = new ArrayList<>();
        for (List<String> values : records.subList(1, records.size())) {
            Map<String, String> row = new LinkedHashMap<>();
            for (int index = 0; index < Math.min(header.size(), values.size()); index++) {
                row.put(header.get(index), values.get(index));
            }
            rows.add(row);
        }
        return new CsvTable(header, rows);
    }

    private static List<List<String>> parseCsv(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < content.length() && content.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                record.add(field.toString());
                field.setLength(0);
            } else if ((character == '\n' || character == '\r') && !quoted) {
                if (character == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    index++;
                }
                record.add(field.toString());
                field.setLength(0);
                if (!record.stream().allMatch(String::isEmpty)) {
                    records.add(List.copyOf(record));
                }
                record.clear();
            } else {
                field.append(character);
            }
        }
        assertThat(quoted).as("balanced CSV quotes").isFalse();
        if (!field.isEmpty() || !record.isEmpty()) {
            record.add(field.toString());
            records.add(List.copyOf(record));
        }
        return records;
    }

    private record CsvTable(List<String> header, List<Map<String, String>> rows) {
    }
}
