ALTER TABLE app.import_quarantine
    DROP CONSTRAINT ck_import_quarantine_reason;

ALTER TABLE app.import_quarantine
    ADD CONSTRAINT ck_import_quarantine_reason CHECK (
        reason_code IN (
            'CONTRACT_UNKNOWN_FIELD',
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
    );
