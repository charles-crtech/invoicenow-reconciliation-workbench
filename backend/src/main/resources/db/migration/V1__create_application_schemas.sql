CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS audit;

COMMENT ON SCHEMA app IS
    'Transactional application data for the synthetic InvoiceNow workbench.';
COMMENT ON SCHEMA audit IS
    'Append-only audit evidence; application write restrictions are added with the audit model.';
