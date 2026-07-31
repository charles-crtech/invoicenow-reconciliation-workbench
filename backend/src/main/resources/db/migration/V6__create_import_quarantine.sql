CREATE TABLE app.import_quarantine (
    id UUID PRIMARY KEY,
    import_batch_id UUID NOT NULL,
    source_name VARCHAR(255) NOT NULL,
    record_number BIGINT NOT NULL,
    record_type VARCHAR(32) NOT NULL,
    source_payload_hash VARCHAR(64) NOT NULL,
    original_record TEXT NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    field_name VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_import_quarantine_batch FOREIGN KEY (import_batch_id)
        REFERENCES app.import_batches (id) ON DELETE RESTRICT,
    CONSTRAINT uq_import_quarantine_record UNIQUE (
        import_batch_id, source_name, record_type, record_number
    ),
    CONSTRAINT ck_import_quarantine_record_number CHECK (record_number > 0),
    CONSTRAINT ck_import_quarantine_record_type CHECK (
        record_type ~ '^[A-Z][A-Z0-9_]{2,31}$'
    ),
    CONSTRAINT ck_import_quarantine_hash CHECK (
        source_payload_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_import_quarantine_original_bound CHECK (
        octet_length(original_record) <= 65536
    ),
    CONSTRAINT ck_import_quarantine_reason CHECK (
        reason_code IN (
            'CONTRACT_REQUIRED_FIELD',
            'CONTRACT_VALUE_INVALID',
            'CONTRACT_SYNTHETIC_ID_REQUIRED',
            'CONTRACT_LEDGER_SIDE',
            'CONTRACT_CURRENCY_MISMATCH',
            'CONTRACT_INVOICE_HEADER_MISMATCH',
            'CONTRACT_SUPPLIER_REFERENCE',
            'CONTRACT_DUPLICATE_IDENTITY',
            'CONTRACT_CSV_FIELD_COUNT'
        )
    ),
    CONSTRAINT ck_import_quarantine_field_name CHECK (
        field_name IS NULL OR field_name ~ '^[a-z][a-z0-9_]{0,63}$'
    )
);

CREATE INDEX ix_import_quarantine_batch_record
    ON app.import_quarantine (import_batch_id, record_number, record_type, id);

COMMENT ON TABLE app.import_quarantine IS
    'Bounded original synthetic records rejected by source-contract validation.';
