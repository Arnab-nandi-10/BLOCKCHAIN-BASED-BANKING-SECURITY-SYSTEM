-- =============================================================================
-- V1__init_transaction_schema.sql
-- Initial schema for the transaction-service database.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ENUM types (PostgreSQL native enums for clean constraint enforcement)
-- -----------------------------------------------------------------------------

CREATE TYPE transaction_type AS ENUM (
    'TRANSFER',
    'PAYMENT',
    'WITHDRAWAL',
    'DEPOSIT'
);

CREATE TYPE transaction_status AS ENUM (
    'SUBMITTED',
    'PENDING_FRAUD_CHECK',
    'VERIFIED',
    'FRAUD_HOLD',
    'BLOCKED',
    'COMPLETED',
    'FAILED'
);

-- -----------------------------------------------------------------------------
-- Main transactions table
-- -----------------------------------------------------------------------------

CREATE TABLE transactions (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    transaction_id    VARCHAR(64)  NOT NULL,
    tenant_id         VARCHAR(64)  NOT NULL,
    from_account      VARCHAR(128) NOT NULL,
    to_account        VARCHAR(128) NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    currency          VARCHAR(3)   NOT NULL,
    type              VARCHAR(32)  NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    blockchain_tx_id  VARCHAR(128),
    block_number      VARCHAR(64),
    fraud_score       DOUBLE PRECISION NOT NULL DEFAULT -1.0,
    fraud_risk_level  VARCHAR(32),
    rejection_reason  VARCHAR(512),
    correlation_id    VARCHAR(64),
    ip_address        VARCHAR(64),
    user_agent        VARCHAR(512),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    completed_at      TIMESTAMP,

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT uq_transactions_transaction_id UNIQUE (transaction_id),
    CONSTRAINT chk_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_transactions_currency CHECK (char_length(currency) = 3)
);

-- -----------------------------------------------------------------------------
-- Indexes on frequently queried columns
-- -----------------------------------------------------------------------------

CREATE INDEX idx_transactions_transaction_id ON transactions (transaction_id);
CREATE INDEX idx_transactions_tenant_id      ON transactions (tenant_id);
CREATE INDEX idx_transactions_from_account   ON transactions (from_account);
CREATE INDEX idx_transactions_status         ON transactions (status);
CREATE INDEX idx_transactions_tenant_status  ON transactions (tenant_id, status);
CREATE INDEX idx_transactions_created_at     ON transactions (created_at DESC);
CREATE INDEX idx_transactions_tenant_created ON transactions (tenant_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- transaction_metadata — JPA @ElementCollection (Map<String, String>)
-- -----------------------------------------------------------------------------

CREATE TABLE transaction_metadata (
    transaction_id UUID        NOT NULL,
    meta_key       VARCHAR(128) NOT NULL,
    meta_value     VARCHAR(1024),

    CONSTRAINT pk_transaction_metadata PRIMARY KEY (transaction_id, meta_key),
    CONSTRAINT fk_transaction_metadata_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES transactions (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_transaction_metadata_tx ON transaction_metadata (transaction_id);

-- -----------------------------------------------------------------------------
-- Trigger: keep updated_at current on every UPDATE
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
