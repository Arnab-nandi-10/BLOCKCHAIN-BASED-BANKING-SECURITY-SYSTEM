CREATE TABLE IF NOT EXISTS simulated_audit_records (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    audit_id            VARCHAR(100)  NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    blockchain_tx_id    VARCHAR(128),
    block_number        VARCHAR(64),
    payload_hash        VARCHAR(128),
    record_hash         VARCHAR(128),
    previous_hash       VARCHAR(128),
    verification_status VARCHAR(32)   NOT NULL DEFAULT 'VERIFIED',
    record_json         TEXT          NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT pk_simulated_audit_records PRIMARY KEY (id),
    CONSTRAINT uq_simulated_audit_records_audit_id UNIQUE (audit_id)
);

CREATE INDEX IF NOT EXISTS idx_simulated_audit_records_audit_id
    ON simulated_audit_records (audit_id);

CREATE INDEX IF NOT EXISTS idx_simulated_audit_records_tenant_id
    ON simulated_audit_records (tenant_id);

CREATE INDEX IF NOT EXISTS idx_simulated_audit_records_created_at
    ON simulated_audit_records (created_at DESC);
