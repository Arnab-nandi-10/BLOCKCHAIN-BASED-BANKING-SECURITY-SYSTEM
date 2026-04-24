ALTER TABLE blockchain_records
    ADD COLUMN IF NOT EXISTS ledger_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_LEDGER',
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(32) NOT NULL DEFAULT 'NOT_VERIFIED',
    ADD COLUMN IF NOT EXISTS payload_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS record_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS last_error VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_blockchain_records_ledger_status
    ON blockchain_records (ledger_status);

CREATE INDEX IF NOT EXISTS idx_blockchain_records_next_retry_at
    ON blockchain_records (next_retry_at)
    WHERE next_retry_at IS NOT NULL;
