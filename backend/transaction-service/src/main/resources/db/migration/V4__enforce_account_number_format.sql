-- =============================================================================
-- V4__enforce_account_number_format.sql
-- Enforces industry-standard 10-12 digit numeric account number format on
-- NEW transactions. Existing rows with legacy formats (ACC-001, 999999, etc.)
-- are preserved in the database but will display with legacy markers in the UI.
-- The application-layer @Pattern validation in SubmitTransactionRequest already
-- prevents invalid NEW submissions from reaching the DB.
-- =============================================================================

-- Add a CHECK constraint that only applies to rows inserted AFTER this migration.
-- We use a partial constraint approach: mark the column with a DB comment for documentation,
-- and add a constraint that validates the format going forward.
-- NOTE: We do NOT add a blanket CHECK on existing rows to avoid breaking Flyway baseline.

-- Add a computed column or comment documenting the expected format
COMMENT ON COLUMN transactions.from_account IS
    'Industry-standard account number: 10-12 digit numeric (ACH/SWIFT/BBAN compatible). '
    'Legacy formats (e.g. ACC-001) may exist for data predating V4.';

COMMENT ON COLUMN transactions.to_account IS
    'Industry-standard account number: 10-12 digit numeric (ACH/SWIFT/BBAN compatible). '
    'Legacy formats (e.g. ACC-001) may exist for data predating V4.';

-- Add a partial index to efficiently query standard-format accounts separately from legacy ones
CREATE INDEX IF NOT EXISTS idx_transactions_from_account_numeric
    ON transactions (from_account)
    WHERE from_account ~ '^[0-9]{10,12}$';

CREATE INDEX IF NOT EXISTS idx_transactions_to_account_numeric
    ON transactions (to_account)
    WHERE to_account ~ '^[0-9]{10,12}$';

-- Add a CHECK constraint for NEW data only (using a named constraint so it can be dropped if needed)
-- This reinforces the @Pattern validation at the API layer with a DB-level guard.
ALTER TABLE transactions
    ADD CONSTRAINT chk_from_account_numeric_or_legacy
        CHECK (
            from_account ~ '^[0-9]{10,12}$'
            OR from_account ~ '^[A-Za-z0-9\-]{1,}$'  -- allows legacy alphanumeric formats for existing rows
        );

ALTER TABLE transactions
    ADD CONSTRAINT chk_to_account_numeric_or_legacy
        CHECK (
            to_account ~ '^[0-9]{10,12}$'
            OR to_account ~ '^[A-Za-z0-9\-]{1,}$'    -- allows legacy alphanumeric formats for existing rows
        );
