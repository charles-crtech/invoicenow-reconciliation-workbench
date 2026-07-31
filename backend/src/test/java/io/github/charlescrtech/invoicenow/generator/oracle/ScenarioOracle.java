package io.github.charlescrtech.invoicenow.generator.oracle;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Test-only expected outcomes. Production source sets cannot depend on this package. */
public record ScenarioOracle(
        String oracleVersion,
        String datasetId,
        String manifestSha256,
        ExpectedImport expectedImport,
        ExpectedReconciliation expectedReconciliation) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public ScenarioOracle {
        if (!"1.0".equals(oracleVersion)) {
            throw new IllegalArgumentException("oracle version must be 1.0");
        }
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("oracle dataset ID must be present");
        }
        if (manifestSha256 == null || !manifestSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("oracle manifest checksum must be lowercase SHA-256");
        }
        Objects.requireNonNull(expectedImport, "expected import must not be null");
        Objects.requireNonNull(expectedReconciliation, "expected reconciliation must not be null");
    }

    public static ScenarioOracle load(Path path) throws IOException {
        JsonNode root = JSON.readTree(Files.readString(path, StandardCharsets.UTF_8));
        requireFields(root, Set.of(
                "oracle_version",
                "dataset_id",
                "manifest_sha256",
                "expected_import",
                "expected_reconciliation"));

        JsonNode imported = requiredObject(root, "expected_import");
        requireFields(imported, Set.of("accepted", "rejected_record_count", "quarantined_record_count"));
        JsonNode accepted = requiredObject(imported, "accepted");
        requireFields(accepted, Set.of("suppliers", "invoices", "invoice_lines", "ledger_entries"));

        JsonNode reconciliation = requiredObject(root, "expected_reconciliation");
        requireFields(reconciliation, Set.of(
                "matched_invoice_count", "unexplained_difference", "expected_exceptions"));
        JsonNode exceptionNodes = reconciliation.get("expected_exceptions");
        if (exceptionNodes == null || !exceptionNodes.isArray()) {
            throw new IllegalArgumentException("expected_exceptions must be an array");
        }
        List<ExpectedException> exceptions = new ArrayList<>();
        for (JsonNode exception : exceptionNodes) {
            requireFields(exception, Set.of(
                    "scenario_id", "document_reference", "reason_code", "financial_impact"));
            exceptions.add(new ExpectedException(
                    requiredString(exception, "scenario_id"),
                    requiredString(exception, "document_reference"),
                    requiredString(exception, "reason_code"),
                    requiredDecimal(exception, "financial_impact")));
        }

        return new ScenarioOracle(
                requiredString(root, "oracle_version"),
                requiredString(root, "dataset_id"),
                requiredString(root, "manifest_sha256"),
                new ExpectedImport(
                        new RecordCounts(
                                requiredInt(accepted, "suppliers"),
                                requiredInt(accepted, "invoices"),
                                requiredInt(accepted, "invoice_lines"),
                                requiredInt(accepted, "ledger_entries")),
                        requiredInt(imported, "rejected_record_count"),
                        requiredInt(imported, "quarantined_record_count")),
                new ExpectedReconciliation(
                        requiredInt(reconciliation, "matched_invoice_count"),
                        requiredDecimal(reconciliation, "unexplained_difference"),
                        exceptions));
    }

    public void verifyBinding(String actualDatasetId, String actualManifestSha256) {
        if (!datasetId.equals(actualDatasetId)) {
            throw new IllegalArgumentException("oracle dataset ID does not match the manifest");
        }
        if (!manifestSha256.equals(actualManifestSha256)) {
            throw new IllegalArgumentException("oracle checksum does not match the manifest bytes");
        }
    }

    public record RecordCounts(int suppliers, int invoices, int invoiceLines, int ledgerEntries) {
        public RecordCounts {
            if (suppliers < 0 || invoices < 0 || invoiceLines < 0 || ledgerEntries < 0) {
                throw new IllegalArgumentException("oracle accepted counts must not be negative");
            }
        }

        public int total() {
            return Math.addExact(
                    Math.addExact(suppliers, invoices),
                    Math.addExact(invoiceLines, ledgerEntries));
        }
    }

    public record ExpectedImport(
            RecordCounts accepted,
            int rejectedRecordCount,
            int quarantinedRecordCount) {
        public ExpectedImport {
            Objects.requireNonNull(accepted, "accepted counts must not be null");
            if (rejectedRecordCount < 0 || quarantinedRecordCount < 0) {
                throw new IllegalArgumentException("oracle disposition counts must not be negative");
            }
        }
    }

    public record ExpectedReconciliation(
            int matchedInvoiceCount,
            BigDecimal unexplainedDifference,
            List<ExpectedException> exceptions) {
        public ExpectedReconciliation {
            if (matchedInvoiceCount < 0) {
                throw new IllegalArgumentException("matched invoice count must not be negative");
            }
            Objects.requireNonNull(unexplainedDifference, "unexplained difference must not be null");
            exceptions = List.copyOf(exceptions);
        }
    }

    public record ExpectedException(
            String scenarioId,
            String documentReference,
            String reasonCode,
            BigDecimal financialImpact) {
        public ExpectedException {
            if (scenarioId == null || scenarioId.isBlank()
                    || documentReference == null || documentReference.isBlank()
                    || reasonCode == null || reasonCode.isBlank()) {
                throw new IllegalArgumentException("expected exception identity must be present");
            }
            Objects.requireNonNull(financialImpact, "expected financial impact must not be null");
        }
    }

    private static JsonNode requiredObject(JsonNode parent, String name) {
        JsonNode value = parent.get(name);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        return value;
    }

    private static String requiredString(JsonNode parent, String name) {
        JsonNode value = parent.get(name);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw new IllegalArgumentException(name + " must be a nonblank string");
        }
        return value.stringValue();
    }

    private static int requiredInt(JsonNode parent, String name) {
        JsonNode value = parent.get(name);
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        try {
            return value.bigIntegerValue().intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " must fit in a signed 32-bit integer", exception);
        }
    }

    private static BigDecimal requiredDecimal(JsonNode parent, String name) {
        JsonNode value = parent.get(name);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        return value.decimalValue();
    }

    private static void requireFields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject() || !node.propertyNames().equals(expected)) {
            throw new IllegalArgumentException("oracle object must contain exactly " + expected);
        }
    }
}
