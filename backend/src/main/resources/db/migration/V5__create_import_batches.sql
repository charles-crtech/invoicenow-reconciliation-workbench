CREATE TABLE app.import_batches (
    id UUID PRIMARY KEY,
    dataset_id VARCHAR(64) NOT NULL,
    contract_version VARCHAR(16) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    source_size_bytes BIGINT NOT NULL,
    source_sha256 VARCHAR(64) NOT NULL,
    manifest_sha256 VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    accepted_count BIGINT NOT NULL DEFAULT 0,
    rejected_count BIGINT NOT NULL DEFAULT 0,
    quarantined_count BIGINT NOT NULL DEFAULT 0,
    failure_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_import_batches_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uq_import_batches_content_fingerprint UNIQUE (
        dataset_id, contract_version, source_type, source_sha256, manifest_sha256
    ),
    CONSTRAINT ck_import_batches_dataset_id CHECK (
        dataset_id ~ '^[a-z0-9][a-z0-9._-]{2,63}$'
    ),
    CONSTRAINT ck_import_batches_contract_version CHECK (contract_version = '1.0'),
    CONSTRAINT ck_import_batches_source_type CHECK (source_type IN ('CSV', 'JSON')),
    CONSTRAINT ck_import_batches_source_name CHECK (
        source_name = btrim(source_name)
        AND source_name !~ '[/\\]'
        AND source_name !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_import_batches_content_type CHECK (
        (source_type = 'CSV' AND content_type = 'text/csv')
        OR (source_type = 'JSON' AND content_type = 'application/json')
    ),
    CONSTRAINT ck_import_batches_source_size CHECK (
        source_size_bytes BETWEEN 1 AND 268435456
    ),
    CONSTRAINT ck_import_batches_source_hash CHECK (
        source_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_import_batches_manifest_hash CHECK (
        manifest_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_import_batches_idempotency_key CHECK (
        idempotency_key ~ '^[A-Za-z0-9._:-]{8,128}$'
    ),
    CONSTRAINT ck_import_batches_status CHECK (
        status IN ('REGISTERED', 'PROCESSING', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT ck_import_batches_counts CHECK (
        accepted_count >= 0 AND rejected_count >= 0 AND quarantined_count >= 0
    ),
    CONSTRAINT ck_import_batches_failure_code CHECK (
        failure_code IS NULL OR failure_code ~ '^[A-Z][A-Z0-9_]{2,63}$'
    ),
    CONSTRAINT ck_import_batches_lifecycle CHECK (
        (status = 'REGISTERED' AND started_at IS NULL AND completed_at IS NULL AND failure_code IS NULL)
        OR (status = 'PROCESSING' AND started_at IS NOT NULL AND completed_at IS NULL AND failure_code IS NULL)
        OR (status = 'COMPLETED' AND started_at IS NOT NULL AND completed_at IS NOT NULL AND failure_code IS NULL)
        OR (status = 'FAILED' AND started_at IS NOT NULL AND completed_at IS NOT NULL AND failure_code IS NOT NULL)
    ),
    CONSTRAINT ck_import_batches_time_order CHECK (
        (started_at IS NULL OR started_at >= created_at)
        AND (completed_at IS NULL OR completed_at >= started_at)
    ),
    CONSTRAINT ck_import_batches_version CHECK (version >= 0)
);

CREATE INDEX ix_import_batches_status_created
    ON app.import_batches (status, created_at DESC, id DESC);

COMMENT ON TABLE app.import_batches IS
    'Bounded synthetic ingestion registrations and governed checksum replay decisions.';
