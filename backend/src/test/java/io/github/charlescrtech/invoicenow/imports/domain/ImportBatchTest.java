package io.github.charlescrtech.invoicenow.imports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ImportBatchTest {

    private static final Instant CREATED = Instant.parse("2026-07-31T10:00:00Z");

    @Test
    void registersBoundedBatchWithoutLeakingIdempotencyKey() {
        ImportBatch batch = batch("import-key-0001", "a".repeat(64));

        assertThat(batch.status()).isEqualTo(ImportBatchStatus.REGISTERED);
        assertThat(batch.acceptedCount()).isZero();
        assertThat(batch.rejectedCount()).isZero();
        assertThat(batch.quarantinedCount()).isZero();
        assertThat(batch.startedAt()).isEmpty();
        assertThat(batch.completedAt()).isEmpty();
        assertThat(batch.failureCode()).isEmpty();
        assertThat(batch.version()).isEmpty();
        assertThat(batch.idempotencyKey().toString()).isEqualTo("[redacted-idempotency-key]");
    }

    @Test
    void validatesDatasetContractAndSafeSourceName() {
        assertThatThrownBy(() -> batch("import-key-0001", "a".repeat(64), "Bad ID", "dataset.json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImportBatch.register(
                        ImportBatchId.newId(), "smoke-001", "2.0", ImportSourceType.JSON,
                        "dataset.json", "application/json", 100, hash('a'), hash('b'),
                        key("import-key-0001"), CREATED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> batch("import-key-0001", "a".repeat(64), "smoke-001", "../dataset.json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> batch("import-key-0001", "a".repeat(64), "smoke-001", " dataset.json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> batch("import-key-0001", "a".repeat(64), "smoke-001", "bad\nname.json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sourceTypeControlsExactContentType() {
        assertThatThrownBy(() -> ImportBatch.register(
                        ImportBatchId.newId(), "smoke-001", "1.0", ImportSourceType.JSON,
                        "dataset.json", "text/csv", 100, hash('a'), hash('b'),
                        key("import-key-0001"), CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content type");
        ImportBatch csv = ImportBatch.register(
                ImportBatchId.newId(), "smoke-001", "1.0", ImportSourceType.CSV,
                "invoices.csv", "text/csv", 100, hash('a'), hash('b'),
                key("import-key-0001"), CREATED);
        assertThat(csv.contentType()).isEqualTo("text/csv");
    }

    @Test
    void sourceSizeIsPositiveAndCapped() {
        assertThatThrownBy(() -> batchWithSize(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> batchWithSize(ImportBatch.MAX_SOURCE_SIZE_BYTES + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(batchWithSize(ImportBatch.MAX_SOURCE_SIZE_BYTES).sourceSizeBytes())
                .isEqualTo(ImportBatch.MAX_SOURCE_SIZE_BYTES);
    }

    @Test
    void hashesAndIdempotencyKeysAreStrict() {
        assertThatThrownBy(() -> new Sha256Hash("ABC"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey("short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey("invalid key spaces"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new IdempotencyKey("client:batch_0001").value()).isEqualTo("client:batch_0001");
    }

    @Test
    void fingerprintIgnoresClientKeyButSameKeyReplayRequiresRegistrationDetails() {
        ImportBatch first = batch("import-key-0001", "a".repeat(64));
        ImportBatch sameContent = batch("import-key-0002", "a".repeat(64));
        ImportBatch changedContent = batch("import-key-0001", "c".repeat(64));

        assertThat(first.fingerprint()).isEqualTo(sameContent.fingerprint());
        assertThat(first.hasSameRegistrationDetails(sameContent)).isTrue();
        assertThat(first.hasSameRegistrationDetails(changedContent)).isFalse();
    }

    @Test
    void restoredLifecycleMustBeInternallyConsistent() {
        ImportBatch completed = ImportBatch.restore(
                ImportBatchId.newId(), "smoke-001", "1.0", ImportSourceType.JSON,
                "dataset.json", "application/json", 100, hash('a'), hash('b'), key("import-key-0001"),
                ImportBatchStatus.COMPLETED, 10, 1, 1, null, CREATED,
                CREATED.plusSeconds(1), CREATED.plusSeconds(2), 3L);
        assertThat(completed.version()).hasValue(3);

        assertThatThrownBy(() -> ImportBatch.restore(
                        ImportBatchId.newId(), "smoke-001", "1.0", ImportSourceType.JSON,
                        "dataset.json", "application/json", 100, hash('a'), hash('b'),
                        key("import-key-0001"), ImportBatchStatus.FAILED, 0, 0, 0, null,
                        CREATED, CREATED.plusSeconds(1), CREATED.plusSeconds(2), 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImportBatch.restore(
                        ImportBatchId.newId(), "smoke-001", "1.0", ImportSourceType.JSON,
                        "dataset.json", "application/json", 100, hash('a'), hash('b'),
                        key("import-key-0001"), ImportBatchStatus.PROCESSING, -1, 0, 0, null,
                        CREATED, CREATED.plusSeconds(1), null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registeredBatchCanStartAndCompleteWithReconciledCounts() {
        ImportBatch processing = batch("import-key-0001", "a".repeat(64))
                .start(CREATED.plusSeconds(1));
        ImportBatch completed = processing.complete(10, 2, 3, CREATED.plusSeconds(2));

        assertThat(processing.status()).isEqualTo(ImportBatchStatus.PROCESSING);
        assertThat(completed.status()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(completed.acceptedCount()).isEqualTo(10);
        assertThat(completed.rejectedCount()).isEqualTo(2);
        assertThat(completed.quarantinedCount()).isEqualTo(3);
        assertThat(completed.failureCode()).isEmpty();
    }

    @Test
    void processingBatchCanFailWithoutClaimingPartialAcceptedOrQuarantinedRows() {
        ImportBatch failed = batch("import-key-0001", "a".repeat(64))
                .start(CREATED.plusSeconds(1))
                .fail("IMPORT_TRANSACTION_FAILED", 7, CREATED.plusSeconds(2));

        assertThat(failed.status()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(failed.acceptedCount()).isZero();
        assertThat(failed.rejectedCount()).isEqualTo(7);
        assertThat(failed.quarantinedCount()).isZero();
        assertThat(failed.failureCode()).contains("IMPORT_TRANSACTION_FAILED");
    }

    @Test
    void lifecycleTransitionsRejectWrongStatesAndTimeOrder() {
        ImportBatch registered = batch("import-key-0001", "a".repeat(64));
        ImportBatch processing = registered.start(CREATED.plusSeconds(1));

        assertThatThrownBy(() -> registered.complete(1, 0, 0, CREATED.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registered.fail("IMPORT_FAILED", 1, CREATED.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> processing.start(CREATED.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> processing.complete(1, 0, 0, CREATED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ImportBatch batch(String key, String sourceHash) {
        return batch(key, sourceHash, "smoke-001", "dataset.json");
    }

    private static ImportBatch batch(String key, String sourceHash, String datasetId, String sourceName) {
        return ImportBatch.register(
                ImportBatchId.newId(), datasetId, "1.0", ImportSourceType.JSON,
                sourceName, "application/json", 100, new Sha256Hash(sourceHash), hash('b'),
                key(key), CREATED);
    }

    private static ImportBatch batchWithSize(long size) {
        return ImportBatch.register(
                ImportBatchId.newId(), "smoke-001", "1.0", ImportSourceType.JSON,
                "dataset.json", "application/json", size, hash('a'), hash('b'),
                key("import-key-0001"), CREATED);
    }

    private static Sha256Hash hash(char value) {
        return new Sha256Hash(String.valueOf(value).repeat(64));
    }

    private static IdempotencyKey key(String value) {
        return new IdempotencyKey(value);
    }
}
