package io.github.charlescrtech.invoicenow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.charlescrtech.invoicenow.imports.application.ImportBatchRegistration;
import io.github.charlescrtech.invoicenow.imports.application.ImportBatchService;
import io.github.charlescrtech.invoicenow.imports.application.JsonImportCoordinator;
import io.github.charlescrtech.invoicenow.imports.application.JsonImportException;
import io.github.charlescrtech.invoicenow.imports.application.JsonImportResult;
import io.github.charlescrtech.invoicenow.imports.application.QuarantineQueryService;
import io.github.charlescrtech.invoicenow.imports.application.RegisterImportBatchCommand;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchStatus;
import io.github.charlescrtech.invoicenow.imports.domain.ImportSourceType;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JsonImportIntegrationTest {

    private static final Path SMOKE = Path.of("..", "data", "smoke", "v1")
            .toAbsolutePath()
            .normalize();
    private static final Path INVALID = Path.of("..", "contracts", "source", "v1", "fixtures", "invalid", "json")
            .toAbsolutePath()
            .normalize();
    private static final String SMOKE_DATASET = "smoke-v1-seed-20260731";

    @Autowired
    private ImportBatchService batches;

    @Autowired
    private JsonImportCoordinator json;

    @Autowired
    private QuarantineQueryService quarantine;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearBusinessData() {
        jdbc.update("DELETE FROM app.import_quarantine");
        jdbc.update("DELETE FROM app.import_batches");
        jdbc.update("DELETE FROM app.invoice_lines");
        jdbc.update("DELETE FROM app.invoices");
        jdbc.update("DELETE FROM app.ledger_entries");
        jdbc.update("DELETE FROM app.suppliers");
    }

    @AfterEach
    void clearBusinessDataAfterTest() {
        clearBusinessData();
    }

    @Test
    void migrationAddsTheStableJsonQuarantineReason() {
        Integer migration = jdbc.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE version = '7' AND success",
                Integer.class);
        String definition = jdbc.queryForObject(
                """
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conname = 'ck_import_quarantine_reason'
                """,
                String.class);

        assertThat(migration).isEqualTo(1);
        assertThat(definition).contains("CONTRACT_UNKNOWN_FIELD");
    }

    @Test
    void smokeJsonPersists413SourceUnitsOnce() throws Exception {
        Path source = SMOKE.resolve("dataset.json");
        byte[] bytes = Files.readAllBytes(source);
        String manifestHash = sha(Files.readAllBytes(SMOKE.resolve("manifest.json")));
        ImportBatchRegistration registered = register(
                SMOKE_DATASET, bytes, manifestHash, "json-smoke-import-01");

        ImportBatch completed;
        try (InputStream input = Files.newInputStream(source)) {
            completed = json.importJson(
                    registered.batch().id(), "dataset.json", "application/json", bytes.length, input).batch();
        }

        assertThat(completed.status()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(completed.acceptedCount()).isEqualTo(413);
        assertThat(completed.rejectedCount()).isZero();
        assertThat(completed.quarantinedCount()).isZero();
        assertThat(count("suppliers")).isEqualTo(10);
        assertThat(count("invoices")).isEqualTo(100);
        assertThat(count("invoice_lines")).isEqualTo(203);
        assertThat(count("ledger_entries")).isEqualTo(100);

        JsonImportResult replay = json.importJson(
                completed.id(), "ignored-after-completion", "ignored/type", -1, InputStream.nullInputStream());
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.batch().id()).isEqualTo(completed.id());
        assertThat(count("invoices")).isEqualTo(100);
        assertThat(count("invoice_lines")).isEqualTo(203);
    }

    @Test
    void committedInvalidCasesPersistStableBoundedQuarantineEvidence() throws Exception {
        byte[] unknown = Files.readAllBytes(INVALID.resolve("unknown-field.json"));
        ImportBatch unknownBatch = importBytes(
                "invalid-unknown-field", unknown, "json-unknown-field-01");

        assertThat(unknownBatch.acceptedCount()).isZero();
        assertThat(unknownBatch.quarantinedCount()).isEqualTo(1);
        assertThat(quarantine.get(unknownBatch.id(), 10, 0).records()).singleElement().satisfies(record -> {
            assertThat(record.reason().name()).isEqualTo("CONTRACT_UNKNOWN_FIELD");
            assertThat(record.fieldName()).isEqualTo("unexpected");
            assertThat(record.originalRecord()).contains("closed schemas reject this field");
        });

        clearBusinessData();
        byte[] currency = Files.readAllBytes(INVALID.resolve("mixed-line-currency.json"));
        ImportBatch currencyBatch = importBytes(
                "invalid-mixed-currency", currency, "json-mixed-currency-1");

        assertThat(currencyBatch.acceptedCount()).isEqualTo(1);
        assertThat(currencyBatch.quarantinedCount()).isEqualTo(2);
        assertThat(count("suppliers")).isEqualTo(1);
        assertThat(count("invoices")).isZero();
        assertThat(quarantine.get(currencyBatch.id(), 10, 0).records())
                .extracting(record -> record.recordType() + ":" + record.reason().name())
                .containsExactlyInAnyOrder(
                        "INVOICE:CONTRACT_CURRENCY_MISMATCH",
                        "INVOICE_LINE:CONTRACT_CURRENCY_MISMATCH");
    }

    @Test
    void fatalParserFailureLeavesNoPartialRowsAndMarksBatchFailed() {
        byte[] bytes = envelope(
                "fatal-json-case",
                "SUP-001",
                "x".repeat(4_097),
                "SYNTH-UEN-000001");
        ImportBatchRegistration registered = register(
                "fatal-json-case", bytes, "a".repeat(64), "json-fatal-limit-01");

        assertThatThrownBy(() -> json.importJson(
                registered.batch().id(),
                "dataset.json",
                "application/json",
                bytes.length,
                new ByteArrayInputStream(bytes)))
                .isInstanceOfSatisfying(JsonImportException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IMPORT_JSON_LIMIT_EXCEEDED"));

        ImportBatch failed = batches.get(registered.batch().id());
        assertThat(failed.status()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(failed.failureCode()).contains("IMPORT_JSON_LIMIT_EXCEEDED");
        assertThat(failed.acceptedCount()).isZero();
        assertThat(count("suppliers")).isZero();
        assertThat(count("import_quarantine")).isZero();
    }

    @Test
    void databaseFailureRollsBackJsonUnitAndThenMarksBatchFailed() {
        byte[] first = envelope(
                "json-first-case", "SUP-001", "First", "SYNTH-UEN-000001");
        importBytes("json-first-case", first, "json-first-case-01");

        byte[] duplicateRegistration = envelope(
                "json-second-case", "SUP-002", "Second", "SYNTH-UEN-000001");
        ImportBatchRegistration second = register(
                "json-second-case", duplicateRegistration, "b".repeat(64), "json-second-case01");

        assertThatThrownBy(() -> json.importJson(
                second.batch().id(),
                "dataset.json",
                "application/json",
                duplicateRegistration.length,
                new ByteArrayInputStream(duplicateRegistration)))
                .isInstanceOfSatisfying(JsonImportException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IMPORT_TRANSACTION_FAILED"));

        assertThat(count("suppliers")).isEqualTo(1);
        assertThat(count("import_quarantine")).isZero();
        ImportBatch failed = batches.get(second.batch().id());
        assertThat(failed.status()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(failed.failureCode()).contains("IMPORT_TRANSACTION_FAILED");
    }

    private ImportBatch importBytes(String datasetId, byte[] bytes, String key) {
        ImportBatchRegistration registration = register(datasetId, bytes, "f".repeat(64), key);
        return json.importJson(
                registration.batch().id(),
                "dataset.json",
                "application/json",
                bytes.length,
                new ByteArrayInputStream(bytes)).batch();
    }

    private ImportBatchRegistration register(
            String datasetId,
            byte[] bytes,
            String manifestHash,
            String key) {
        return batches.register(new RegisterImportBatchCommand(
                datasetId,
                "1.0",
                ImportSourceType.JSON,
                "dataset.json",
                "application/json",
                bytes.length,
                sha(bytes),
                manifestHash,
                key));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM app." + table, Long.class);
    }

    private static byte[] envelope(
            String datasetId,
            String supplierCode,
            String displayName,
            String registration) {
        return """
                {"contract_version":"1.0","dataset_id":"%s","suppliers":[
                  {"supplier_code":"%s","display_name":"%s","registration_identifier":"%s",
                   "gst_registered":true,"status":"ACTIVE"}],"invoices":[],"ledger_entries":[]}
                """.formatted(datasetId, supplierCode, displayName, registration)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
