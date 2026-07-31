package io.github.charlescrtech.invoicenow.imports.infrastructure.persistence;

import io.github.charlescrtech.invoicenow.imports.application.ImportBatchRepository;
import io.github.charlescrtech.invoicenow.imports.domain.IdempotencyKey;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchFingerprint;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchStatus;
import io.github.charlescrtech.invoicenow.imports.domain.ImportSourceType;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class PostgresImportBatchRepository implements ImportBatchRepository {

    private static final String COLUMNS = """
            id, dataset_id, contract_version, source_type, source_name, content_type,
            source_size_bytes, source_sha256, manifest_sha256, idempotency_key, status,
            accepted_count, rejected_count, quarantined_count, failure_code, created_at,
            started_at, completed_at, version
            """;
    private static final RowMapper<ImportBatch> ROW_MAPPER = PostgresImportBatchRepository::map;

    private final JdbcTemplate jdbcTemplate;

    PostgresImportBatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean insertIfAbsent(ImportBatch batch) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO app.import_batches (
                    id, dataset_id, contract_version, source_type, source_name, content_type,
                    source_size_bytes, source_sha256, manifest_sha256, idempotency_key, status,
                    accepted_count, rejected_count, quarantined_count, failure_code, created_at,
                    started_at, completed_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                batch.id().value(),
                batch.datasetId(),
                batch.contractVersion(),
                batch.sourceType().name(),
                batch.sourceName(),
                batch.contentType(),
                batch.sourceSizeBytes(),
                batch.sourceSha256().value(),
                batch.manifestSha256().value(),
                batch.idempotencyKey().value(),
                batch.status().name(),
                batch.acceptedCount(),
                batch.rejectedCount(),
                batch.quarantinedCount(),
                batch.failureCode().orElse(null),
                Timestamp.from(batch.createdAt()),
                batch.startedAt().map(Timestamp::from).orElse(null),
                batch.completedAt().map(Timestamp::from).orElse(null),
                batch.version().isPresent() ? batch.version().getAsLong() : 0L);
        return inserted == 1;
    }

    @Override
    public Optional<ImportBatch> findById(ImportBatchId id) {
        return first(jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM app.import_batches WHERE id = ?",
                ROW_MAPPER,
                id.value()));
    }

    @Override
    public Optional<ImportBatch> findByIdempotencyKey(IdempotencyKey key) {
        return first(jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM app.import_batches WHERE idempotency_key = ?",
                ROW_MAPPER,
                key.value()));
    }

    @Override
    public Optional<ImportBatch> findByFingerprint(ImportBatchFingerprint fingerprint) {
        return first(jdbcTemplate.query(
                """
                SELECT %s FROM app.import_batches
                WHERE dataset_id = ? AND contract_version = ? AND source_type = ?
                    AND source_sha256 = ? AND manifest_sha256 = ?
                """.formatted(COLUMNS),
                ROW_MAPPER,
                fingerprint.datasetId(),
                fingerprint.contractVersion(),
                fingerprint.sourceType().name(),
                fingerprint.sourceSha256().value(),
                fingerprint.manifestSha256().value()));
    }

    @Override
    public Optional<ImportBatch> update(ImportBatch batch) {
        long expectedVersion = batch.version().orElseThrow(
                () -> new IllegalArgumentException("a persisted batch version is required"));
        int updated = jdbcTemplate.update(
                """
                UPDATE app.import_batches
                SET status = ?, accepted_count = ?, rejected_count = ?, quarantined_count = ?,
                    failure_code = ?, started_at = ?, completed_at = ?, version = version + 1
                WHERE id = ? AND version = ?
                """,
                batch.status().name(),
                batch.acceptedCount(),
                batch.rejectedCount(),
                batch.quarantinedCount(),
                batch.failureCode().orElse(null),
                batch.startedAt().map(Timestamp::from).orElse(null),
                batch.completedAt().map(Timestamp::from).orElse(null),
                batch.id().value(),
                expectedVersion);
        return updated == 1 ? findById(batch.id()) : Optional.empty();
    }

    private static Optional<ImportBatch> first(List<ImportBatch> batches) {
        return batches.stream().findFirst();
    }

    private static ImportBatch map(ResultSet result, int rowNumber) throws SQLException {
        return ImportBatch.restore(
                new ImportBatchId(result.getObject("id", java.util.UUID.class)),
                result.getString("dataset_id"),
                result.getString("contract_version"),
                ImportSourceType.valueOf(result.getString("source_type")),
                result.getString("source_name"),
                result.getString("content_type"),
                result.getLong("source_size_bytes"),
                new Sha256Hash(result.getString("source_sha256")),
                new Sha256Hash(result.getString("manifest_sha256")),
                new IdempotencyKey(result.getString("idempotency_key")),
                ImportBatchStatus.valueOf(result.getString("status")),
                result.getLong("accepted_count"),
                result.getLong("rejected_count"),
                result.getLong("quarantined_count"),
                result.getString("failure_code"),
                result.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                instant(result, "started_at"),
                instant(result, "completed_at"),
                result.getLong("version"));
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        java.time.OffsetDateTime value = result.getObject(column, java.time.OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
