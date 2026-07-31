package io.github.charlescrtech.invoicenow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.charlescrtech.invoicenow.imports.application.ImportBatchRegistration;
import io.github.charlescrtech.invoicenow.imports.application.ImportBatchService;
import io.github.charlescrtech.invoicenow.imports.application.ImportIdempotencyConflictException;
import io.github.charlescrtech.invoicenow.imports.application.RegisterImportBatchCommand;
import io.github.charlescrtech.invoicenow.imports.domain.ImportSourceType;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImportBatchIntegrationTest {

    @Autowired
    private ImportBatchService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearImportBatches() {
        jdbcTemplate.update("DELETE FROM app.import_batches");
    }

    @Test
    void migrationCreatesImportTableAndNamedConstraints() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.flyway_schema_history WHERE version = '5' AND success",
                Integer.class);
        List<String> constraints = jdbcTemplate.queryForList(
                """
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE table_schema = 'app' AND table_name = 'import_batches'
                """,
                String.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(constraints).contains(
                "import_batches_pkey",
                "uq_import_batches_idempotency_key",
                "uq_import_batches_content_fingerprint",
                "ck_import_batches_dataset_id",
                "ck_import_batches_contract_version",
                "ck_import_batches_source_type",
                "ck_import_batches_source_name",
                "ck_import_batches_content_type",
                "ck_import_batches_source_size",
                "ck_import_batches_source_hash",
                "ck_import_batches_manifest_hash",
                "ck_import_batches_idempotency_key",
                "ck_import_batches_status",
                "ck_import_batches_counts",
                "ck_import_batches_lifecycle",
                "ck_import_batches_time_order",
                "ck_import_batches_version");
    }

    @Test
    void newRegistrationAndBothReplayPoliciesProduceOneBatch() {
        ImportBatchRegistration created = service.register(command("import-key-0001", 'a', "dataset.json"));
        ImportBatchRegistration sameKey = service.register(command("import-key-0001", 'a', "dataset.json"));
        ImportBatchRegistration sameContentNewKey = service.register(command("import-key-0002", 'a', "renamed.json"));

        assertThat(created.replayed()).isFalse();
        assertThat(sameKey.replayed()).isTrue();
        assertThat(sameContentNewKey.replayed()).isTrue();
        assertThat(sameKey.batch().id()).isEqualTo(created.batch().id());
        assertThat(sameContentNewKey.batch().id()).isEqualTo(created.batch().id());
        assertThat(batchCount()).isEqualTo(1);
    }

    @Test
    void reusingKeyForDifferentRegistrationIsConflict() {
        service.register(command("import-key-0001", 'a', "dataset.json"));

        assertThatThrownBy(() -> service.register(command("import-key-0001", 'c', "dataset.json")))
                .isInstanceOf(ImportIdempotencyConflictException.class);
        assertThat(batchCount()).isEqualTo(1);
    }

    @Test
    void concurrentSameChecksumRegistrationResolvesOneGovernedOutcome() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ImportBatchRegistration> first = executor.submit(() -> registerWhenReleased(
                    ready, start, command("import-key-0001", 'a', "dataset.json")));
            Future<ImportBatchRegistration> second = executor.submit(() -> registerWhenReleased(
                    ready, start, command("import-key-0002", 'a', "renamed.json")));
            ready.await();
            start.countDown();

            ImportBatchRegistration firstResult = first.get();
            ImportBatchRegistration secondResult = second.get();
            assertThat(firstResult.batch().id()).isEqualTo(secondResult.batch().id());
            assertThat(List.of(firstResult.replayed(), secondResult.replayed()))
                    .containsExactlyInAnyOrder(false, true);
            assertThat(batchCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void registrationAndReplayDoNotCreateBusinessRecords() {
        long suppliers = tableCount("suppliers");
        long invoices = tableCount("invoices");
        long ledger = tableCount("ledger_entries");

        service.register(command("import-key-0001", 'a', "dataset.json"));
        service.register(command("import-key-0001", 'a', "dataset.json"));

        assertThat(tableCount("suppliers")).isEqualTo(suppliers);
        assertThat(tableCount("invoices")).isEqualTo(invoices);
        assertThat(tableCount("ledger_entries")).isEqualTo(ledger);
    }

    @Test
    void databaseRejectsRawInvalidTypeSizeHashCountsAndLifecycle() {
        assertThatThrownBy(() -> rawInsert(
                        "invalid-type", "XML", "application/xml", 10, "a".repeat(64), 0, "REGISTERED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> rawInsert(
                        "invalid-size", "JSON", "application/json", 0, "a".repeat(64), 0, "REGISTERED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> rawInsert(
                        "invalid-hash", "JSON", "application/json", 10, "bad", 0, "REGISTERED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> rawInsert(
                        "invalid-count", "JSON", "application/json", 10, "a".repeat(64), -1, "REGISTERED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> rawInsert(
                        "invalid-life", "JSON", "application/json", 10, "a".repeat(64), 0, "FAILED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ImportBatchRegistration registerWhenReleased(
            CountDownLatch ready,
            CountDownLatch start,
            RegisterImportBatchCommand command) throws Exception {
        ready.countDown();
        start.await();
        return service.register(command);
    }

    private static RegisterImportBatchCommand command(String key, char sourceHash, String name) {
        return new RegisterImportBatchCommand(
                "smoke-v1-seed-20260731",
                "1.0",
                ImportSourceType.JSON,
                name,
                "application/json",
                128_279,
                String.valueOf(sourceHash).repeat(64),
                "b".repeat(64),
                key);
    }

    private long batchCount() {
        return tableCount("import_batches");
    }

    private long tableCount(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app." + table, Long.class);
        return count == null ? 0 : count;
    }

    private void rawInsert(
            String key,
            String type,
            String contentType,
            long size,
            String sourceHash,
            long acceptedCount,
            String status,
            String failureCode) {
        jdbcTemplate.update(
                """
                INSERT INTO app.import_batches (
                    id, dataset_id, contract_version, source_type, source_name, content_type,
                    source_size_bytes, source_sha256, manifest_sha256, idempotency_key, status,
                    accepted_count, rejected_count, quarantined_count, failure_code, created_at, version
                ) VALUES (?, 'smoke-001', '1.0', ?, 'dataset.json', ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, NOW(), 0)
                """,
                java.util.UUID.randomUUID(), type, contentType, size, sourceHash, "b".repeat(64),
                key, status, acceptedCount, failureCode);
    }
}
