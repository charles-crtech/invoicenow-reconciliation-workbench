package io.github.charlescrtech.invoicenow.imports.domain;

import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record QuarantineRecord(
        UUID id,
        ImportBatchId batchId,
        String sourceName,
        long recordNumber,
        String recordType,
        Sha256Hash sourcePayloadHash,
        String originalRecord,
        QuarantineReason reason,
        String fieldName,
        Instant createdAt) {

    public static final int MAX_ORIGINAL_BYTES = 65_536;
    private static final Pattern RECORD_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{2,31}");
    private static final Pattern FIELD_NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    public QuarantineRecord {
        Objects.requireNonNull(id, "quarantine ID must not be null");
        Objects.requireNonNull(batchId, "import batch ID must not be null");
        Objects.requireNonNull(sourceName, "source name must not be null");
        if (sourceName.isBlank() || sourceName.length() > 255) {
            throw new IllegalArgumentException("source name must be bounded");
        }
        if (recordNumber < 1) {
            throw new IllegalArgumentException("record number must be positive");
        }
        Objects.requireNonNull(recordType, "record type must not be null");
        if (!RECORD_TYPE.matcher(recordType).matches()) {
            throw new IllegalArgumentException("record type must be a stable code");
        }
        Objects.requireNonNull(sourcePayloadHash, "source payload hash must not be null");
        Objects.requireNonNull(originalRecord, "original record must not be null");
        if (originalRecord.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_ORIGINAL_BYTES) {
            throw new IllegalArgumentException("original record exceeds the quarantine bound");
        }
        Objects.requireNonNull(reason, "quarantine reason must not be null");
        if (fieldName != null && !FIELD_NAME.matcher(fieldName).matches()) {
            throw new IllegalArgumentException("field name must be an allowlisted identifier");
        }
        Objects.requireNonNull(createdAt, "quarantine time must not be null");
    }
}
