CREATE TABLE app.ledger_entries (
    id UUID PRIMARY KEY,
    source_system VARCHAR(32) NOT NULL,
    source_record_id VARCHAR(100) NOT NULL,
    account_code VARCHAR(32) NOT NULL,
    counterparty_reference VARCHAR(64) NOT NULL,
    document_reference VARCHAR(64) NOT NULL,
    posting_date DATE NOT NULL,
    reporting_period_start DATE NOT NULL,
    reporting_period_end DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    debit_amount NUMERIC(19, 4) NOT NULL,
    credit_amount NUMERIC(19, 4) NOT NULL,
    tax_amount NUMERIC(19, 4) NOT NULL,
    source_payload_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_ledger_entries_source_identity UNIQUE (source_system, source_record_id),
    CONSTRAINT ck_ledger_entries_source_system_format CHECK (
        source_system ~ '^[A-Z][A-Z0-9_]{2,31}$'
    ),
    CONSTRAINT ck_ledger_entries_source_record_id_format CHECK (
        source_record_id ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}$'
    ),
    CONSTRAINT ck_ledger_entries_account_code_format CHECK (
        account_code ~ '^[A-Z0-9][A-Z0-9._-]{2,31}$'
    ),
    CONSTRAINT ck_ledger_entries_counterparty_format CHECK (
        counterparty_reference ~ '^[A-Z0-9][A-Z0-9._/-]{0,63}$'
    ),
    CONSTRAINT ck_ledger_entries_document_format CHECK (
        document_reference ~ '^[A-Z0-9][A-Z0-9._/-]{0,63}$'
    ),
    CONSTRAINT ck_ledger_entries_reporting_period CHECK (
        reporting_period_start < reporting_period_end
    ),
    CONSTRAINT ck_ledger_entries_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ledger_entries_debit_credit CHECK (
        (debit_amount > 0 AND credit_amount = 0)
        OR (credit_amount > 0 AND debit_amount = 0)
    ),
    CONSTRAINT ck_ledger_entries_tax_amount CHECK (tax_amount >= 0),
    CONSTRAINT ck_ledger_entries_source_hash_format CHECK (
        source_payload_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_ledger_entries_version CHECK (version >= 0)
);

CREATE INDEX ix_ledger_entries_matching
    ON app.ledger_entries (counterparty_reference, document_reference, posting_date);
CREATE INDEX ix_ledger_entries_reporting_account
    ON app.ledger_entries (reporting_period_start, reporting_period_end, account_code);

COMMENT ON TABLE app.ledger_entries IS
    'Immutable synthetic ledger evidence for reconciliation exercises; not a general ledger.';
