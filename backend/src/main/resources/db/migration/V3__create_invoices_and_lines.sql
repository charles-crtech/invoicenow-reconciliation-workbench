CREATE TABLE app.invoices (
    id UUID PRIMARY KEY,
    source_system VARCHAR(32) NOT NULL,
    source_record_id VARCHAR(100) NOT NULL,
    supplier_id UUID NOT NULL,
    document_number VARCHAR(64) NOT NULL,
    document_type VARCHAR(16) NOT NULL,
    issue_date DATE NOT NULL,
    posting_date DATE NOT NULL,
    reporting_period_start DATE NOT NULL,
    reporting_period_end DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    declared_net NUMERIC(19, 4) NOT NULL,
    declared_tax NUMERIC(19, 4) NOT NULL,
    declared_gross NUMERIC(19, 4) NOT NULL,
    source_payload_hash VARCHAR(64) NOT NULL,
    record_status VARCHAR(16) NOT NULL,
    last_correction_reason VARCHAR(500),
    void_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_invoices_supplier FOREIGN KEY (supplier_id)
        REFERENCES app.suppliers (id) ON DELETE RESTRICT,
    CONSTRAINT uq_invoices_source_identity UNIQUE (source_system, source_record_id),
    CONSTRAINT uq_invoices_document_identity UNIQUE (
        source_system, supplier_id, document_type, document_number
    ),
    CONSTRAINT ck_invoices_source_system_format CHECK (
        source_system ~ '^[A-Z][A-Z0-9_]{2,31}$'
    ),
    CONSTRAINT ck_invoices_source_record_id_format CHECK (
        source_record_id ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}$'
    ),
    CONSTRAINT ck_invoices_document_number_format CHECK (
        document_number ~ '^[A-Z0-9][A-Z0-9._/-]{0,63}$'
    ),
    CONSTRAINT ck_invoices_document_type CHECK (
        document_type IN ('INVOICE', 'CREDIT_NOTE', 'DEBIT_NOTE')
    ),
    CONSTRAINT ck_invoices_date_order CHECK (posting_date >= issue_date),
    CONSTRAINT ck_invoices_reporting_period CHECK (
        reporting_period_start < reporting_period_end
    ),
    CONSTRAINT ck_invoices_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_invoices_source_hash_format CHECK (
        source_payload_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_invoices_status CHECK (record_status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT ck_invoices_correction_reason_format CHECK (
        last_correction_reason IS NULL OR (
            last_correction_reason = BTRIM(last_correction_reason)
            AND CHAR_LENGTH(last_correction_reason) BETWEEN 10 AND 500
        )
    ),
    CONSTRAINT ck_invoices_void_state CHECK (
        (record_status = 'ACTIVE' AND void_reason IS NULL)
        OR (
            record_status = 'VOIDED'
            AND void_reason = BTRIM(void_reason)
            AND CHAR_LENGTH(void_reason) BETWEEN 10 AND 500
        )
    ),
    CONSTRAINT ck_invoices_version CHECK (version >= 0),
    CONSTRAINT ck_invoices_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX ix_invoices_supplier ON app.invoices (supplier_id);
CREATE INDEX ix_invoices_reporting_period
    ON app.invoices (reporting_period_start, reporting_period_end);

CREATE TABLE app.invoice_lines (
    invoice_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    description VARCHAR(500) NOT NULL,
    item_code VARCHAR(64),
    quantity NUMERIC(19, 4) NOT NULL,
    unit_price NUMERIC(19, 4) NOT NULL,
    net_amount NUMERIC(19, 4) NOT NULL,
    tax_category VARCHAR(20) NOT NULL,
    tax_rate NUMERIC(7, 4) NOT NULL,
    tax_amount NUMERIC(19, 4) NOT NULL,
    gross_amount NUMERIC(19, 4) NOT NULL,
    CONSTRAINT invoice_lines_pkey PRIMARY KEY (invoice_id, line_number),
    CONSTRAINT fk_invoice_lines_invoice FOREIGN KEY (invoice_id)
        REFERENCES app.invoices (id) ON DELETE RESTRICT,
    CONSTRAINT ck_invoice_lines_line_number CHECK (line_number > 0),
    CONSTRAINT ck_invoice_lines_description_format CHECK (
        description = BTRIM(description)
        AND CHAR_LENGTH(description) BETWEEN 1 AND 500
    ),
    CONSTRAINT ck_invoice_lines_item_code_format CHECK (
        item_code IS NULL OR item_code ~ '^[A-Za-z0-9][A-Za-z0-9._/-]{0,63}$'
    ),
    CONSTRAINT ck_invoice_lines_quantity CHECK (quantity > 0),
    CONSTRAINT ck_invoice_lines_tax_category CHECK (
        tax_category IN ('STANDARD_RATED', 'ZERO_RATED', 'EXEMPT', 'OUT_OF_SCOPE')
    ),
    CONSTRAINT ck_invoice_lines_tax_rate CHECK (tax_rate BETWEEN 0 AND 1)
);

COMMENT ON TABLE app.invoices IS
    'Synthetic invoice headers preserving declared source values for reconciliation exercises.';
COMMENT ON TABLE app.invoice_lines IS
    'Synthetic invoice lines; monetary currency is inherited from the owning invoice.';
