-- =============================================================================
-- V1__init_blockchain_schema.sql
-- Initial schema for the blockchain-service local cache database (bbss_blockchain).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- blockchain_records — local cache of transactions committed to the Fabric ledger
-- -----------------------------------------------------------------------------

CREATE TABLE blockchain_records (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    transaction_id   VARCHAR(64)   NOT NULL,
    tenant_id        VARCHAR(64)   NOT NULL,
    blockchain_tx_id VARCHAR(128),
    block_number     VARCHAR(64),
    chaincode_id     VARCHAR(128),
    payload          TEXT,
    status           VARCHAR(32),
    created_at       TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT pk_blockchain_records PRIMARY KEY (id),
    CONSTRAINT uq_blockchain_records_transaction_id UNIQUE (transaction_id)
);

-- -----------------------------------------------------------------------------
-- Indexes for fast look-up patterns
-- -----------------------------------------------------------------------------

CREATE INDEX idx_blockchain_records_transaction_id ON blockchain_records (transaction_id);
CREATE INDEX idx_blockchain_records_tenant_id      ON blockchain_records (tenant_id);
CREATE INDEX idx_blockchain_records_created_at     ON blockchain_records (created_at DESC);
CREATE INDEX idx_blockchain_records_tenant_created ON blockchain_records (tenant_id, created_at DESC);
CREATE INDEX idx_blockchain_records_blockchain_txid ON blockchain_records (blockchain_tx_id)
    WHERE blockchain_tx_id IS NOT NULL;
