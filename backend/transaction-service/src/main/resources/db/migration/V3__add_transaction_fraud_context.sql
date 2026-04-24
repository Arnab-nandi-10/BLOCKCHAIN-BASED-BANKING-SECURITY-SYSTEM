ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS fraud_decision VARCHAR(16),
    ADD COLUMN IF NOT EXISTS fraud_recommendation VARCHAR(128),
    ADD COLUMN IF NOT EXISTS review_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS payload_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS record_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_transactions_fraud_decision
    ON transactions (fraud_decision);

CREATE INDEX IF NOT EXISTS idx_transactions_review_required
    ON transactions (review_required);

CREATE TABLE IF NOT EXISTS transaction_triggered_rules (
    transaction_id UUID NOT NULL,
    rule_value VARCHAR(255) NOT NULL,
    CONSTRAINT fk_transaction_triggered_rules_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES transactions (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_transaction_triggered_rules_tx
    ON transaction_triggered_rules (transaction_id);

CREATE TABLE IF NOT EXISTS transaction_explanations (
    transaction_id UUID NOT NULL,
    explanation VARCHAR(512) NOT NULL,
    CONSTRAINT fk_transaction_explanations_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES transactions (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_transaction_explanations_tx
    ON transaction_explanations (transaction_id);
