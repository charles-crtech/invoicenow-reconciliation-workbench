package io.github.charlescrtech.invoicenow.imports.domain;

import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

public final class ImportBatch {

    public static final long MAX_SOURCE_SIZE_BYTES = 268_435_456L;

    private static final Pattern DATASET_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{2,63}");
    private static final Pattern FAILURE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    private final ImportBatchId id;
    private final String datasetId;
    private final String contractVersion;
    private final ImportSourceType sourceType;
    private final String sourceName;
    private final String contentType;
    private final long sourceSizeBytes;
    private final Sha256Hash sourceSha256;
    private final Sha256Hash manifestSha256;
    private final IdempotencyKey idempotencyKey;
    private final ImportBatchStatus status;
    private final long acceptedCount;
    private final long rejectedCount;
    private final long quarantinedCount;
    private final String failureCode;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Long version;

    private ImportBatch(
            ImportBatchId id,
            String datasetId,
            String contractVersion,
            ImportSourceType sourceType,
            String sourceName,
            String contentType,
            long sourceSizeBytes,
            Sha256Hash sourceSha256,
            Sha256Hash manifestSha256,
            IdempotencyKey idempotencyKey,
            ImportBatchStatus status,
            long acceptedCount,
            long rejectedCount,
            long quarantinedCount,
            String failureCode,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Long version) {
        this.id = Objects.requireNonNull(id, "import batch ID must not be null");
        this.datasetId = validDatasetId(datasetId);
        this.contractVersion = validContractVersion(contractVersion);
        this.sourceType = Objects.requireNonNull(sourceType, "source type must not be null");
        this.sourceName = validSourceName(sourceName);
        this.contentType = validContentType(sourceType, contentType);
        this.sourceSizeBytes = validSourceSize(sourceSizeBytes);
        this.sourceSha256 = Objects.requireNonNull(sourceSha256, "source checksum must not be null");
        this.manifestSha256 = Objects.requireNonNull(manifestSha256, "manifest checksum must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotency key must not be null");
        this.status = Objects.requireNonNull(status, "import batch status must not be null");
        this.acceptedCount = nonNegative(acceptedCount, "accepted count");
        this.rejectedCount = nonNegative(rejectedCount, "rejected count");
        this.quarantinedCount = nonNegative(quarantinedCount, "quarantined count");
        this.failureCode = validFailureCode(failureCode);
        this.createdAt = Objects.requireNonNull(createdAt, "created time must not be null");
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.version = validVersion(version);
        validateLifecycle();
    }

    public static ImportBatch register(
            ImportBatchId id,
            String datasetId,
            String contractVersion,
            ImportSourceType sourceType,
            String sourceName,
            String contentType,
            long sourceSizeBytes,
            Sha256Hash sourceSha256,
            Sha256Hash manifestSha256,
            IdempotencyKey idempotencyKey,
            Instant createdAt) {
        return new ImportBatch(
                id, datasetId, contractVersion, sourceType, sourceName, contentType, sourceSizeBytes,
                sourceSha256, manifestSha256, idempotencyKey, ImportBatchStatus.REGISTERED,
                0, 0, 0, null, createdAt, null, null, null);
    }

    public static ImportBatch restore(
            ImportBatchId id,
            String datasetId,
            String contractVersion,
            ImportSourceType sourceType,
            String sourceName,
            String contentType,
            long sourceSizeBytes,
            Sha256Hash sourceSha256,
            Sha256Hash manifestSha256,
            IdempotencyKey idempotencyKey,
            ImportBatchStatus status,
            long acceptedCount,
            long rejectedCount,
            long quarantinedCount,
            String failureCode,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Long version) {
        return new ImportBatch(
                id, datasetId, contractVersion, sourceType, sourceName, contentType, sourceSizeBytes,
                sourceSha256, manifestSha256, idempotencyKey, status, acceptedCount, rejectedCount,
                quarantinedCount, failureCode, createdAt, startedAt, completedAt, version);
    }

    public ImportBatchFingerprint fingerprint() {
        return new ImportBatchFingerprint(
                datasetId, contractVersion, sourceType, sourceSha256, manifestSha256);
    }

    public boolean hasSameRegistrationDetails(ImportBatch other) {
        Objects.requireNonNull(other, "candidate import batch must not be null");
        return fingerprint().equals(other.fingerprint())
                && sourceName.equals(other.sourceName)
                && contentType.equals(other.contentType)
                && sourceSizeBytes == other.sourceSizeBytes;
    }

    public ImportBatchId id() { return id; }
    public String datasetId() { return datasetId; }
    public String contractVersion() { return contractVersion; }
    public ImportSourceType sourceType() { return sourceType; }
    public String sourceName() { return sourceName; }
    public String contentType() { return contentType; }
    public long sourceSizeBytes() { return sourceSizeBytes; }
    public Sha256Hash sourceSha256() { return sourceSha256; }
    public Sha256Hash manifestSha256() { return manifestSha256; }
    public IdempotencyKey idempotencyKey() { return idempotencyKey; }
    public ImportBatchStatus status() { return status; }
    public long acceptedCount() { return acceptedCount; }
    public long rejectedCount() { return rejectedCount; }
    public long quarantinedCount() { return quarantinedCount; }
    public Optional<String> failureCode() { return Optional.ofNullable(failureCode); }
    public Instant createdAt() { return createdAt; }
    public Optional<Instant> startedAt() { return Optional.ofNullable(startedAt); }
    public Optional<Instant> completedAt() { return Optional.ofNullable(completedAt); }
    public OptionalLong version() { return version == null ? OptionalLong.empty() : OptionalLong.of(version); }

    private void validateLifecycle() {
        boolean valid = switch (status) {
            case REGISTERED -> startedAt == null && completedAt == null && failureCode == null;
            case PROCESSING -> startedAt != null && completedAt == null && failureCode == null;
            case COMPLETED -> startedAt != null && completedAt != null && failureCode == null;
            case FAILED -> startedAt != null && completedAt != null && failureCode != null;
        };
        if (!valid) {
            throw new IllegalArgumentException("import batch timestamps and failure code must match status");
        }
        if (startedAt != null && startedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("import start must not precede creation");
        }
        if (completedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("import completion must not precede start");
        }
    }

    private static String validDatasetId(String value) {
        Objects.requireNonNull(value, "dataset ID must not be null");
        if (!DATASET_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("dataset ID must satisfy source contract v1");
        }
        return value;
    }

    private static String validContractVersion(String value) {
        if (!"1.0".equals(value)) {
            throw new IllegalArgumentException("contract version must be 1.0");
        }
        return value;
    }

    private static String validSourceName(String value) {
        Objects.requireNonNull(value, "source name must not be null");
        if (value.isBlank() || !value.equals(value.strip()) || value.codePointCount(0, value.length()) > 255
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("source name must be a safe bounded base name");
        }
        return value;
    }

    private static String validContentType(ImportSourceType type, String value) {
        Objects.requireNonNull(value, "content type must not be null");
        if (!type.contentType().equals(value)) {
            throw new IllegalArgumentException("content type does not match source type");
        }
        return value;
    }

    private static long validSourceSize(long value) {
        if (value < 1 || value > MAX_SOURCE_SIZE_BYTES) {
            throw new IllegalArgumentException("source size must be between 1 and " + MAX_SOURCE_SIZE_BYTES);
        }
        return value;
    }

    private static long nonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static String validFailureCode(String value) {
        if (value != null && !FAILURE_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("failure code must be a bounded stable code");
        }
        return value;
    }

    private static Long validVersion(Long value) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException("import batch version must not be negative");
        }
        return value;
    }
}
