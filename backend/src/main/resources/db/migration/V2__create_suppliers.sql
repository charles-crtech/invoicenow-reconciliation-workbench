CREATE TABLE app.suppliers (
    id UUID PRIMARY KEY,
    supplier_code VARCHAR(32) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    registration_identifier VARCHAR(64) NOT NULL,
    gst_registered BOOLEAN NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_suppliers_supplier_code UNIQUE (supplier_code),
    CONSTRAINT uq_suppliers_registration_identifier UNIQUE (registration_identifier),
    CONSTRAINT ck_suppliers_supplier_code_format CHECK (
        supplier_code ~ '^[A-Z0-9][A-Z0-9_-]{2,31}$'
    ),
    CONSTRAINT ck_suppliers_display_name_format CHECK (
        display_name = BTRIM(display_name)
        AND CHAR_LENGTH(display_name) BETWEEN 1 AND 200
    ),
    CONSTRAINT ck_suppliers_registration_identifier_format CHECK (
        registration_identifier ~ '^SYNTH-[A-Z0-9][A-Z0-9-]{2,57}$'
    ),
    CONSTRAINT ck_suppliers_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_suppliers_version CHECK (version >= 0),
    CONSTRAINT ck_suppliers_timestamps CHECK (updated_at >= created_at)
);

COMMENT ON TABLE app.suppliers IS
    'Synthetic suppliers used by the educational reconciliation workbench.';
COMMENT ON COLUMN app.suppliers.registration_identifier IS
    'Explicitly synthetic registration-shaped identifier; never a live company UEN.';
