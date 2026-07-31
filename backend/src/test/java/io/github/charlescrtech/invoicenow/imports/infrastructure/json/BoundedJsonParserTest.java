package io.github.charlescrtech.invoicenow.imports.infrastructure.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.charlescrtech.invoicenow.imports.application.JsonImportException;
import io.github.charlescrtech.invoicenow.imports.application.JsonImportPlan;
import io.github.charlescrtech.invoicenow.imports.domain.IdempotencyKey;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import io.github.charlescrtech.invoicenow.imports.domain.ImportSourceType;
import io.github.charlescrtech.invoicenow.imports.domain.QuarantineReason;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class BoundedJsonParserTest {

    private static final Path CONTRACT = Path.of("..", "contracts", "source", "v1")
            .toAbsolutePath()
            .normalize();

    private final BoundedJsonParser parser = new BoundedJsonParser();

    @Test
    void streamsValidFixtureWithExactBatchHashAndBoundedCanonicalRecords() throws Exception {
        byte[] bytes = Files.readAllBytes(CONTRACT.resolve("fixtures/valid/dataset.json"));

        JsonImportPlan plan = parser.parse(
                new ByteArrayInputStream(bytes), batch("contract-smoke-001", bytes));

        assertThat(plan.sourceSizeBytes()).isEqualTo(bytes.length);
        assertThat(plan.sourceSha256()).isEqualTo(sha(bytes));
        assertThat(plan.sourceUnitCount()).isEqualTo(4);
        assertThat(plan.suppliers()).hasSize(1);
        assertThat(plan.invoiceSources()).hasSize(1);
        assertThat(plan.invoiceLines()).hasSize(1);
        assertThat(plan.ledgerEntries()).hasSize(1);
        assertThat(plan.rejectedRows()).isEmpty();
        assertThat(plan.invoiceSources().getFirst().source().original())
                .startsWith("{\"source_system\":")
                .contains("\"lines\":[{");
    }

    @Test
    void canonicalHashIgnoresWhitespaceAndPropertyOrderWhileBatchHashDoesNot() {
        byte[] compact = envelope(validSupplier()).getBytes(StandardCharsets.UTF_8);
        byte[] reordered = envelope("""
                { "status" : "ACTIVE", "gst_registered" : true,
                  "registration_identifier" : "SYNTH-UEN-000001",
                  "display_name" : "Synthetic Supplier", "supplier_code" : "SUP-001" }
                """).getBytes(StandardCharsets.UTF_8);

        JsonImportPlan first = parser.parse(new ByteArrayInputStream(compact), batch("canonical-case", compact));
        JsonImportPlan second = parser.parse(new ByteArrayInputStream(reordered), batch("canonical-case", reordered));

        assertThat(first.sourceSha256()).isNotEqualTo(second.sourceSha256());
        assertThat(first.suppliers().getFirst().source().hash())
                .isEqualTo(second.suppliers().getFirst().source().hash());
        assertThat(first.suppliers().getFirst().source().original())
                .isEqualTo(second.suppliers().getFirst().source().original());
    }

    @Test
    void committedInvalidFixturesRetainStableReasons() throws Exception {
        byte[] unknown = Files.readAllBytes(CONTRACT.resolve("fixtures/invalid/json/unknown-field.json"));
        JsonImportPlan unknownPlan = parser.parse(
                new ByteArrayInputStream(unknown), batch("invalid-unknown-field", unknown));

        assertThat(unknownPlan.rejectedRows()).singleElement().satisfies(row -> {
            assertThat(row.reason()).isEqualTo(QuarantineReason.CONTRACT_UNKNOWN_FIELD);
            assertThat(row.fieldName()).isEqualTo("unexpected");
            assertThat(row.recordType()).isEqualTo("DATASET");
        });

        byte[] currency = Files.readAllBytes(CONTRACT.resolve("fixtures/invalid/json/mixed-line-currency.json"));
        JsonImportPlan currencyPlan = parser.parse(
                new ByteArrayInputStream(currency), batch("invalid-mixed-currency", currency));

        assertThat(currencyPlan.suppliers()).hasSize(1);
        assertThat(currencyPlan.invoiceLines()).isEmpty();
        assertThat(currencyPlan.rejectedRows())
                .hasSize(2)
                .allMatch(row -> row.reason() == QuarantineReason.CONTRACT_CURRENCY_MISMATCH);
    }

    @Test
    void rejectsBomDuplicatePropertiesMalformedSyntaxAndTrailingContent() {
        byte[] valid = envelope(validSupplier()).getBytes(StandardCharsets.UTF_8);
        byte[] bom = new byte[valid.length + 3];
        bom[0] = (byte) 0xef;
        bom[1] = (byte) 0xbb;
        bom[2] = (byte) 0xbf;
        System.arraycopy(valid, 0, bom, 3, valid.length);
        assertCode(bom, "canonical-case", "IMPORT_JSON_BOM");

        byte[] duplicate = envelope(validSupplier())
                .replace("\"dataset_id\":\"canonical-case\"",
                        "\"dataset_id\":\"canonical-case\",\"dataset_id\":\"canonical-case\"")
                .getBytes(StandardCharsets.UTF_8);
        assertCode(duplicate, "canonical-case", "IMPORT_JSON_SYNTAX");

        byte[] malformed = "{\"contract_version\":\"1.0\"".getBytes(StandardCharsets.UTF_8);
        assertCode(malformed, "canonical-case", "IMPORT_JSON_SYNTAX");

        byte[] malformedUtf8 = valid.clone();
        int marker = new String(malformedUtf8, StandardCharsets.UTF_8).indexOf("Synthetic Supplier");
        malformedUtf8[marker] = (byte) 0xc3;
        malformedUtf8[marker + 1] = (byte) 0x28;
        assertCode(malformedUtf8, "canonical-case", "IMPORT_JSON_SYNTAX");

        byte[] trailing = (envelope(validSupplier()) + " {}").getBytes(StandardCharsets.UTF_8);
        assertCode(trailing, "canonical-case", "IMPORT_JSON_TRAILING_CONTENT");
    }

    @Test
    void enforcesRegisteredSizeLogicalRecordAndSourceUnitBounds() {
        byte[] valid = envelope(validSupplier()).getBytes(StandardCharsets.UTF_8);
        ImportBatch wrongSize = batch("canonical-case", valid, valid.length + 1L);
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(valid), wrongSize))
                .isInstanceOfSatisfying(JsonImportException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IMPORT_SOURCE_SIZE_MISMATCH"));

        assertThatThrownBy(() -> new BoundedJsonParser(32, 10)
                .parse(new ByteArrayInputStream(valid), batch("canonical-case", valid)))
                .isInstanceOfSatisfying(JsonImportException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IMPORT_JSON_RECORD_TOO_LARGE"));

        assertThatThrownBy(() -> new BoundedJsonParser(1024, 0)
                .parse(new ByteArrayInputStream(valid), batch("canonical-case", valid)))
                .isInstanceOfSatisfying(JsonImportException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IMPORT_JSON_TOO_MANY_RECORDS"));
    }

    @Test
    void rejectsExplicitNullWrongTypesAndOverlongStringsAsRecordEvidence() {
        byte[] explicitNull = envelope(validSupplier().replace(
                "\"display_name\":\"Synthetic Supplier\"", "\"display_name\":null"))
                .getBytes(StandardCharsets.UTF_8);
        JsonImportPlan nullPlan = parser.parse(
                new ByteArrayInputStream(explicitNull), batch("canonical-case", explicitNull));
        assertThat(nullPlan.rejectedRows()).singleElement()
                .extracting(row -> row.reason())
                .isEqualTo(QuarantineReason.CONTRACT_REQUIRED_FIELD);

        byte[] wrongType = envelope(validSupplier().replace(
                "\"gst_registered\":true", "\"gst_registered\":\"true\""))
                .getBytes(StandardCharsets.UTF_8);
        JsonImportPlan typePlan = parser.parse(
                new ByteArrayInputStream(wrongType), batch("canonical-case", wrongType));
        assertThat(typePlan.rejectedRows()).singleElement()
                .extracting(row -> row.reason())
                .isEqualTo(QuarantineReason.CONTRACT_VALUE_INVALID);

        byte[] longString = envelope(validSupplier().replace(
                "Synthetic Supplier", "x".repeat(BoundedJsonParser.MAX_STRING_LENGTH + 1)))
                .getBytes(StandardCharsets.UTF_8);
        assertCode(longString, "canonical-case", "IMPORT_JSON_LIMIT_EXCEEDED");
    }

    @Test
    void enforcesDepthAndDuplicateIdentityRules() {
        String nested = "0";
        for (int depth = 0; depth < BoundedJsonParser.MAX_DEPTH + 1; depth++) {
            nested = "{\"nested\":" + nested + "}";
        }
        byte[] tooDeep = envelope(validSupplier())
                .replace("\"ledger_entries\":[]", "\"ledger_entries\":[],\"deep\":" + nested)
                .getBytes(StandardCharsets.UTF_8);
        assertCode(tooDeep, "canonical-case", "IMPORT_JSON_LIMIT_EXCEEDED");

        String duplicate = validSupplier().replace("SYNTH-UEN-000001", "SYNTH-UEN-000002");
        byte[] duplicateSupplier = envelope(validSupplier() + "," + duplicate)
                .getBytes(StandardCharsets.UTF_8);
        JsonImportPlan plan = parser.parse(
                new ByteArrayInputStream(duplicateSupplier), batch("canonical-case", duplicateSupplier));

        assertThat(plan.suppliers()).hasSize(1);
        assertThat(plan.rejectedRows()).singleElement()
                .extracting(row -> row.reason())
                .isEqualTo(QuarantineReason.CONTRACT_DUPLICATE_IDENTITY);
    }

    private void assertCode(byte[] bytes, String datasetId, String code) {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(bytes), batch(datasetId, bytes)))
                .isInstanceOfSatisfying(JsonImportException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private static String envelope(String supplier) {
        return """
                {"contract_version":"1.0","dataset_id":"canonical-case","suppliers":[%s],
                "invoices":[],"ledger_entries":[]}
                """.formatted(supplier);
    }

    private static String validSupplier() {
        return """
                {"supplier_code":"SUP-001","display_name":"Synthetic Supplier",
                "registration_identifier":"SYNTH-UEN-000001","gst_registered":true,"status":"ACTIVE"}
                """;
    }

    private static ImportBatch batch(String datasetId, byte[] bytes) {
        return batch(datasetId, bytes, bytes.length);
    }

    private static ImportBatch batch(String datasetId, byte[] bytes, long size) {
        return ImportBatch.register(
                ImportBatchId.newId(),
                datasetId,
                "1.0",
                ImportSourceType.JSON,
                "dataset.json",
                "application/json",
                size,
                sha(bytes),
                new Sha256Hash("b".repeat(64)),
                new IdempotencyKey("json-test-key-0001"),
                Instant.parse("2026-07-31T00:00:00Z"));
    }

    private static Sha256Hash sha(byte[] bytes) {
        try {
            return new Sha256Hash(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
