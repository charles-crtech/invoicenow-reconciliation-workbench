package io.github.charlescrtech.invoicenow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.charlescrtech.invoicenow.imports.application.CsvImportCoordinator;
import io.github.charlescrtech.invoicenow.imports.application.CsvImportException;
import io.github.charlescrtech.invoicenow.imports.application.CsvImportResult;
import io.github.charlescrtech.invoicenow.imports.application.ImportBatchRegistration;
import io.github.charlescrtech.invoicenow.imports.application.ImportBatchService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CsvImportIntegrationTest {

    private static final Path SMOKE = Path.of("..", "data", "smoke", "v1").toAbsolutePath().normalize();
    private static final String DATASET_ID = "smoke-v1-seed-20260731";
    private static final String SUPPLIER_HEADER =
            "contract_version,dataset_id,supplier_code,display_name,registration_identifier,gst_registered,status";

    @Autowired
    private ImportBatchService batches;

    @Autowired
    private CsvImportCoordinator csv;

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
    void migrationCreatesConstrainedQuarantineStorage() {
        Integer migration = jdbc.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE version = '6' AND success",
                Integer.class);
        List<String> constraints = jdbc.queryForList(
                """
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema = 'app' AND table_name = 'import_quarantine'
                """,
                String.class);

        assertThat(migration).isEqualTo(1);
        assertThat(constraints).contains(
                "import_quarantine_pkey",
                "fk_import_quarantine_batch",
                "uq_import_quarantine_record",
                "ck_import_quarantine_record_number",
                "ck_import_quarantine_record_type",
                "ck_import_quarantine_hash",
                "ck_import_quarantine_original_bound",
                "ck_import_quarantine_reason",
                "ck_import_quarantine_field_name");
    }

    @Test
    void smokeCsvArtifactsPersistOnceAndReconcileToManifestSourceUnits() throws Exception {
        String manifestHash = sha(Files.readAllBytes(SMOKE.resolve("manifest.json")));

        ImportBatch suppliers = importFile("suppliers.csv", manifestHash, "csv-smoke-suppliers");
        ImportBatch invoices = importFile("invoices.csv", manifestHash, "csv-smoke-invoices-01");
        ImportBatch ledger = importFile("ledger_entries.csv", manifestHash, "csv-smoke-ledger-001");

        assertThat(suppliers.acceptedCount()).isEqualTo(10);
        assertThat(invoices.acceptedCount()).isEqualTo(303);
        assertThat(ledger.acceptedCount()).isEqualTo(100);
        assertThat(suppliers.acceptedCount() + invoices.acceptedCount() + ledger.acceptedCount())
                .isEqualTo(413);
        assertThat(List.of(suppliers, invoices, ledger))
                .allMatch(batch -> batch.status() == ImportBatchStatus.COMPLETED)
                .allMatch(batch -> batch.rejectedCount() == 0)
                .allMatch(batch -> batch.quarantinedCount() == 0);
        assertThat(count("suppliers")).isEqualTo(10);
        assertThat(count("invoices")).isEqualTo(100);
        assertThat(count("invoice_lines")).isEqualTo(203);
        assertThat(count("ledger_entries")).isEqualTo(100);

        Path source = SMOKE.resolve("invoices.csv");
        CsvImportResult replay;
        try (InputStream input = Files.newInputStream(source)) {
            replay = csv.importCsv(
                    invoices.id(), source.getFileName().toString(), "text/csv", Files.size(source), input);
        }
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.batch().id()).isEqualTo(invoices.id());
        assertThat(count("invoices")).isEqualTo(100);
        assertThat(count("invoice_lines")).isEqualTo(203);
    }

    @Test
    void invalidSyntheticIdentifierIsDurablyQuarantinedWithoutRawQueryExposure() throws Exception {
        byte[] bytes = (SUPPLIER_HEADER + "\n"
                + "1.0,quarantine-case,SUP-001,Synthetic Supplier,REAL-UEN-FORBIDDEN,true,ACTIVE\n")
                .getBytes(StandardCharsets.UTF_8);
        ImportBatchRegistration registered = register(
                "quarantine-case", "suppliers.csv", bytes, "a".repeat(64), "csv-quarantine-01");

        ImportBatch completed = csv.importCsv(
                registered.batch().id(),
                "suppliers.csv",
                "text/csv",
                bytes.length,
                new ByteArrayInputStream(bytes)).batch();
        QuarantineQueryService.QuarantinePage page = quarantine.get(completed.id(), 10, 0);

        assertThat(completed.status()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(completed.acceptedCount()).isZero();
        assertThat(completed.rejectedCount()).isZero();
        assertThat(completed.quarantinedCount()).isEqualTo(1);
        assertThat(count("suppliers")).isZero();
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).singleElement().satisfies(record -> {
            assertThat(record.reason().name()).isEqualTo("CONTRACT_SYNTHETIC_ID_REQUIRED");
            assertThat(record.fieldName()).isEqualTo("registration_identifier");
            assertThat(record.originalRecord()).contains("REAL-UEN-FORBIDDEN");
        });
    }

    @Test
    void invoiceWithoutPreviouslyImportedSupplierQuarantinesHeaderAndLine() throws Exception {
        Path source = Path.of("..", "contracts", "source", "v1", "fixtures", "valid", "invoices.csv")
                .toAbsolutePath()
                .normalize();
        byte[] bytes = Files.readAllBytes(source);
        ImportBatchRegistration registered = register(
                "contract-smoke-001", "invoices.csv", bytes, "b".repeat(64), "csv-missing-supplier");

        ImportBatch completed = csv.importCsv(
                registered.batch().id(),
                "invoices.csv",
                "text/csv",
                bytes.length,
                new ByteArrayInputStream(bytes)).batch();

        assertThat(completed.acceptedCount()).isZero();
        assertThat(completed.quarantinedCount()).isEqualTo(2);
        assertThat(quarantine.get(completed.id(), 10, 0).records())
                .extracting(record -> record.recordType() + ":" + record.reason().name())
                .containsExactlyInAnyOrder(
                        "INVOICE:CONTRACT_SUPPLIER_REFERENCE",
                        "INVOICE_LINE:CONTRACT_SUPPLIER_REFERENCE");
        assertThat(quarantine.get(completed.id(), 1, 0).records()).hasSize(1);
        assertThat(quarantine.get(completed.id(), 1, 1).records()).hasSize(1)
                .isNotEqualTo(quarantine.get(completed.id(), 1, 0).records());
        assertThat(count("invoices")).isZero();
    }

    @Test
    void fatalRecordLimitFailureLeavesNoPartialRowsAndRecordsSafeFailedBatch() {
        String valid = "1.0,fatal-case,SUP-001,Synthetic Supplier,SYNTH-UEN-000001,true,ACTIVE";
        byte[] bytes = (SUPPLIER_HEADER + "\n" + valid + "\n" + "x".repeat(65_537) + "\n")
                .getBytes(StandardCharsets.UTF_8);
        ImportBatchRegistration registered = register(
                "fatal-case", "suppliers.csv", bytes, "c".repeat(64), "csv-fatal-limit-01");

        assertThatThrownBy(() -> csv.importCsv(
                registered.batch().id(),
                "suppliers.csv",
                "text/csv",
                bytes.length,
                new ByteArrayInputStream(bytes)))
                .isInstanceOfSatisfying(CsvImportException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IMPORT_CSV_RECORD_TOO_LARGE"));

        ImportBatch failed = batches.get(registered.batch().id());
        assertThat(failed.status()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(failed.failureCode()).contains("IMPORT_CSV_RECORD_TOO_LARGE");
        assertThat(failed.acceptedCount()).isZero();
        assertThat(failed.rejectedCount()).isEqualTo(1);
        assertThat(count("suppliers")).isZero();
        assertThat(count("import_quarantine")).isZero();
    }

    @Test
    void databaseFailureRollsBackNewRowsAndThenMarksBatchFailed() {
        byte[] first = (SUPPLIER_HEADER + "\n"
                + "1.0,first-case,SUP-001,First,SYNTH-UEN-000001,true,ACTIVE\n")
                .getBytes(StandardCharsets.UTF_8);
        ImportBatchRegistration firstBatch = register(
                "first-case", "suppliers.csv", first, "d".repeat(64), "csv-first-case-01");
        csv.importCsv(firstBatch.batch().id(), "suppliers.csv", "text/csv", first.length,
                new ByteArrayInputStream(first));

        byte[] duplicateRegistration = (SUPPLIER_HEADER + "\n"
                + "1.0,second-case,SUP-002,Second,SYNTH-UEN-000001,true,ACTIVE\n")
                .getBytes(StandardCharsets.UTF_8);
        ImportBatchRegistration second = register(
                "second-case", "suppliers.csv", duplicateRegistration, "e".repeat(64), "csv-second-case01");

        assertThatThrownBy(() -> csv.importCsv(
                second.batch().id(),
                "suppliers.csv",
                "text/csv",
                duplicateRegistration.length,
                new ByteArrayInputStream(duplicateRegistration)))
                .isInstanceOfSatisfying(CsvImportException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IMPORT_TRANSACTION_FAILED"));

        assertThat(count("suppliers")).isEqualTo(1);
        assertThat(count("import_quarantine")).isZero();
        ImportBatch failed = batches.get(second.batch().id());
        assertThat(failed.status()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(failed.failureCode()).contains("IMPORT_TRANSACTION_FAILED");
    }

    private ImportBatch importFile(String name, String manifestHash, String key) throws Exception {
        Path source = SMOKE.resolve(name);
        byte[] bytes = Files.readAllBytes(source);
        ImportBatchRegistration registration = register(DATASET_ID, name, bytes, manifestHash, key);
        try (InputStream input = Files.newInputStream(source)) {
            return csv.importCsv(
                    registration.batch().id(), name, "text/csv", Files.size(source), input).batch();
        }
    }

    private ImportBatchRegistration register(
            String datasetId,
            String sourceName,
            byte[] bytes,
            String manifestHash,
            String key) {
        return batches.register(new RegisterImportBatchCommand(
                datasetId,
                "1.0",
                ImportSourceType.CSV,
                sourceName,
                "text/csv",
                bytes.length,
                sha(bytes),
                manifestHash,
                key));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM app." + table, Long.class);
    }

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
