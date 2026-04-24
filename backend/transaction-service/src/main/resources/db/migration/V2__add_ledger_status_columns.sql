ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS ledger_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(32) NOT NULL DEFAULT 'NOT_VERIFIED';

CREATE INDEX IF NOT EXISTS idx_transactions_ledger_status
    ON transactions (ledger_status);

CREATE INDEX IF NOT EXISTS idx_transactions_verification_status
    ON transactions (verification_status);
