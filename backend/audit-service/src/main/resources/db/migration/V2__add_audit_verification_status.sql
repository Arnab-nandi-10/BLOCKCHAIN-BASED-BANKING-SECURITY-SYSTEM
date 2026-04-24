ALTER TABLE audit_entries
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_audit_verification_status
    ON audit_entries (verification_status)
    WHERE verification_status IS NOT NULL;
