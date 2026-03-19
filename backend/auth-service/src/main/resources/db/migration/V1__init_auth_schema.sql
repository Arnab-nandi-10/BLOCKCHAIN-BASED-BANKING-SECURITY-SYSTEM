-- V1__init_auth_schema.sql
-- Initial schema for Authentication Service

-- ============================================================
-- ROLES TABLE
-- ============================================================
CREATE TABLE IF NOT EXISTS roles (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(50)     NOT NULL UNIQUE,
    description VARCHAR(255),
    CONSTRAINT roles_name_not_empty CHECK (char_length(trim(name)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_roles_name ON roles (name);

-- ============================================================
-- USERS TABLE
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    email                   VARCHAR(255)    NOT NULL UNIQUE,
    password_hash           VARCHAR(255)    NOT NULL,
    first_name              VARCHAR(100)    NOT NULL,
    last_name               VARCHAR(100)    NOT NULL,
    enabled                 BOOLEAN         NOT NULL DEFAULT TRUE,
    account_non_locked      BOOLEAN         NOT NULL DEFAULT TRUE,
    failed_login_attempts   INTEGER         NOT NULL DEFAULT 0,
    locked_until            TIMESTAMP,
    tenant_id               VARCHAR(100)    NOT NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_email_not_empty    CHECK (char_length(trim(email)) > 0),
    CONSTRAINT users_tenant_id_not_empty CHECK (char_length(trim(tenant_id)) > 0),
    CONSTRAINT users_failed_attempts_non_negative CHECK (failed_login_attempts >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_tenant
    ON users (email, tenant_id);

CREATE INDEX IF NOT EXISTS idx_users_tenant_id
    ON users (tenant_id);

CREATE INDEX IF NOT EXISTS idx_users_email
    ON users (email);

CREATE INDEX IF NOT EXISTS idx_users_enabled
    ON users (enabled);

CREATE INDEX IF NOT EXISTS idx_users_account_non_locked
    ON users (account_non_locked);

-- ============================================================
-- USER_ROLES JOIN TABLE
-- ============================================================
CREATE TABLE IF NOT EXISTS user_roles (
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     BIGINT      NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles (user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles (role_id);

-- ============================================================
-- REFRESH TOKENS TABLE
-- ============================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(512)    NOT NULL UNIQUE,
    user_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id   VARCHAR(100)    NOT NULL,
    expires_at  TIMESTAMP       NOT NULL,
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT refresh_tokens_token_not_empty CHECK (char_length(trim(token)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token
    ON refresh_tokens (token);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_revoked
    ON refresh_tokens (user_id, revoked);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_revoked
    ON refresh_tokens (revoked);

-- ============================================================
-- AUTO-UPDATE updated_at TRIGGER
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- SEED DEFAULT ROLES
-- ============================================================
INSERT INTO roles (name, description) VALUES
    ('ROLE_SUPER_ADMIN', 'Super Administrator with full system access'),
    ('ROLE_ADMIN',       'Administrator with tenant-level administrative access'),
    ('ROLE_ANALYST',     'Analyst with read-write access to analytics and transactions'),
    ('ROLE_VIEWER',      'Viewer with read-only access to permitted resources')
ON CONFLICT (name) DO NOTHING;
