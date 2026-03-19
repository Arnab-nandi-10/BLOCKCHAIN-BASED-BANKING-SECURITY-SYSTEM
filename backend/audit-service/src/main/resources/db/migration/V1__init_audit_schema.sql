-- =============================================================================
--  V1__init_audit_schema.sql
--  Creates the initial audit_entries table and supporting indexes.
--  Compatible with PostgreSQL 14+.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Main audit entries table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_entries (

    -- ── Surrogate PK ─────────────────────────────────────────────────────────
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- ── Business identity ────────────────────────────────────────────────────
    -- Sourced from the originating event's eventId; drives idempotency.
    audit_id            VARCHAR(100)    NOT NULL,

    -- ── Tenant ───────────────────────────────────────────────────────────────
    tenant_id           VARCHAR(100)    NOT NULL,

    -- ── What happened ────────────────────────────────────────────────────────
    -- Domain category: TRANSACTION | USER | TENANT | ACCOUNT | ROLE
    entity_type         VARCHAR(50)     NOT NULL,
    -- Primary key or external identifier of the affected entity
    entity_id           VARCHAR(255)    NOT NULL,
    -- Action verb: TRANSACTION_SUBMITTED | TRANSACTION_VERIFIED |
    --              TRANSACTION_BLOCKED | FRAUD_DETECTED | USER_LOGIN |
    --              TENANT_PROVISIONED | …
    action              VARCHAR(100)    NOT NULL,

    -- ── Who did it ───────────────────────────────────────────────────────────
    actor_id            VARCHAR(255)    NOT NULL,
    -- USER | SYSTEM | API
    actor_type          VARCHAR(20)     NOT NULL,
    -- IPv4 or IPv6; NULL for system-generated events with no HTTP origin
    ip_address          VARCHAR(45),

    -- ── Event payload ────────────────────────────────────────────────────────
    -- Full JSON snapshot of the originating event; stored verbatim for forensics
    payload             TEXT,

    -- ── Blockchain coordinates ───────────────────────────────────────────────
    -- Set by BlockchainCommitService after successful ledger anchoring
    blockchain_tx_id    VARCHAR(255),
    block_number        VARCHAR(50),

    -- ── Lifecycle status ─────────────────────────────────────────────────────
    -- PENDING | COMMITTED | FAILED
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',

    -- ── Distributed tracing ──────────────────────────────────────────────────
    correlation_id      VARCHAR(100),

    -- ── Timestamps ───────────────────────────────────────────────────────────
    -- Managed by Spring Data JPA @CreatedDate / @LastModifiedDate
    occurred_at         TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,

    -- ── Constraints ──────────────────────────────────────────────────────────
    CONSTRAINT pk_audit_entries          PRIMARY KEY (id),
    CONSTRAINT uq_audit_entries_audit_id UNIQUE      (audit_id),
    CONSTRAINT chk_audit_actor_type      CHECK       (actor_type IN ('USER', 'SYSTEM', 'API')),
    CONSTRAINT chk_audit_status          CHECK       (status     IN ('PENDING', 'COMMITTED', 'FAILED'))
);

-- ---------------------------------------------------------------------------
-- Indexes for high-performance tenant-scoped queries
-- ---------------------------------------------------------------------------

-- Tenant-level listing: the most common query pattern
CREATE INDEX IF NOT EXISTS idx_audit_tenant
    ON audit_entries (tenant_id);

-- Entity history lookup: "show all events for transaction TX-123"
CREATE INDEX IF NOT EXISTS idx_audit_entity
    ON audit_entries (entity_type, entity_id);

-- Action-based filtering: "show all FRAUD_DETECTED events"
CREATE INDEX IF NOT EXISTS idx_audit_action
    ON audit_entries (action);

-- Lifecycle filtering: used by the retry scheduler
CREATE INDEX IF NOT EXISTS idx_audit_status
    ON audit_entries (status);

-- Chronological ordering: used by almost every query
CREATE INDEX IF NOT EXISTS idx_audit_occurred_at
    ON audit_entries (occurred_at DESC);

-- Blockchain lookup: used when verifying on-chain confirmation
CREATE INDEX IF NOT EXISTS idx_audit_blockchain_tx
    ON audit_entries (blockchain_tx_id)
    WHERE blockchain_tx_id IS NOT NULL;

-- Composite index for the most frequent combined query:
-- tenant + time window (date-range API, last-24h summary metric)
CREATE INDEX IF NOT EXISTS idx_audit_tenant_occurred
    ON audit_entries (tenant_id, occurred_at DESC);

-- Composite index supporting tenant-scoped action breakdown queries
CREATE INDEX IF NOT EXISTS idx_audit_tenant_action
    ON audit_entries (tenant_id, action);

-- Composite index supporting tenant + status count queries (summary endpoint)
CREATE INDEX IF NOT EXISTS idx_audit_tenant_status
    ON audit_entries (tenant_id, status);

-- ---------------------------------------------------------------------------
-- Comments for DBA documentation
-- ---------------------------------------------------------------------------
COMMENT ON TABLE  audit_entries                   IS 'Immutable, blockchain-anchored audit trail for all BBSS platform events.';
COMMENT ON COLUMN audit_entries.audit_id          IS 'Business-level unique identifier sourced from the originating event eventId. Used for idempotent ingestion.';
COMMENT ON COLUMN audit_entries.tenant_id         IS 'Owning tenant; all queries are scoped by this column.';
COMMENT ON COLUMN audit_entries.entity_type       IS 'Domain category of the affected entity (TRANSACTION, USER, TENANT, etc.).';
COMMENT ON COLUMN audit_entries.entity_id         IS 'Primary key or external identifier of the affected entity instance.';
COMMENT ON COLUMN audit_entries.action            IS 'Verb describing the audited event (TRANSACTION_VERIFIED, FRAUD_DETECTED, etc.).';
COMMENT ON COLUMN audit_entries.actor_id          IS 'Identifier of the user, service, or system that triggered the event.';
COMMENT ON COLUMN audit_entries.actor_type        IS 'Category of the acting principal: USER, SYSTEM, or API.';
COMMENT ON COLUMN audit_entries.payload           IS 'Full JSON snapshot of the originating event stored verbatim for forensics.';
COMMENT ON COLUMN audit_entries.blockchain_tx_id  IS 'Blockchain transaction hash returned after successful ledger anchoring; NULL while PENDING.';
COMMENT ON COLUMN audit_entries.block_number      IS 'Ledger block number where this record was confirmed; NULL while PENDING.';
COMMENT ON COLUMN audit_entries.status            IS 'Lifecycle state: PENDING (awaiting blockchain), COMMITTED (anchored), FAILED (retry eligible).';
COMMENT ON COLUMN audit_entries.correlation_id    IS 'End-to-end request correlation ID from the X-Correlation-Id header.';
COMMENT ON COLUMN audit_entries.occurred_at       IS 'Instant at which the auditable event originally occurred (set by JPA @CreatedDate).';
COMMENT ON COLUMN audit_entries.updated_at        IS 'Instant of the last status update, e.g. PENDING → COMMITTED (set by JPA @LastModifiedDate).';
