package io.github.charlescrtech.invoicenow.imports.infrastructure.persistence;

import io.github.charlescrtech.invoicenow.imports.application.QuarantineRepository;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import io.github.charlescrtech.invoicenow.imports.domain.QuarantineReason;
import io.github.charlescrtech.invoicenow.imports.domain.QuarantineRecord;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PostgresQuarantineRepository implements QuarantineRepository {

    private final JdbcTemplate jdbcTemplate;

    PostgresQuarantineRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(List<QuarantineRecord> records) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO app.import_quarantine (
                    id, import_batch_id, source_name, record_number, record_type,
                    source_payload_hash, original_record, reason_code, field_name, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                records,
                250,
                (statement, record) -> {
                    statement.setObject(1, record.id());
                    statement.setObject(2, record.batchId().value());
                    statement.setString(3, record.sourceName());
                    statement.setLong(4, record.recordNumber());
                    statement.setString(5, record.recordType());
                    statement.setString(6, record.sourcePayloadHash().value());
                    statement.setString(7, record.originalRecord());
                    statement.setString(8, record.reason().name());
                    statement.setString(9, record.fieldName());
                    statement.setTimestamp(10, Timestamp.from(record.createdAt()));
                });
    }

    @Override
    public List<QuarantineRecord> findByBatch(
            ImportBatchId batchId,
            int limit,
            int offset) {
        if (limit < 1 || limit > 100 || offset < 0 || offset > 500_000) {
            throw new IllegalArgumentException("quarantine query bounds are invalid");
        }
        return jdbcTemplate.query(
                """
                SELECT id, import_batch_id, source_name, record_number, record_type,
                       source_payload_hash, original_record, reason_code, field_name, created_at
                FROM app.import_quarantine
                WHERE import_batch_id = ?
                ORDER BY record_number, record_type, id
                LIMIT ? OFFSET ?
                """,
                (result, rowNumber) -> new QuarantineRecord(
                        result.getObject("id", java.util.UUID.class),
                        new ImportBatchId(result.getObject("import_batch_id", java.util.UUID.class)),
                        result.getString("source_name"),
                        result.getLong("record_number"),
                        result.getString("record_type"),
                        new Sha256Hash(result.getString("source_payload_hash")),
                        result.getString("original_record"),
                        QuarantineReason.valueOf(result.getString("reason_code")),
                        result.getString("field_name"),
                        result.getObject("created_at", java.time.OffsetDateTime.class).toInstant()),
                batchId.value(),
                limit,
                offset);
    }

    @Override
    public long countByBatch(ImportBatchId batchId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.import_quarantine WHERE import_batch_id = ?",
                Long.class,
                batchId.value());
        return count == null ? 0 : count;
    }
}
