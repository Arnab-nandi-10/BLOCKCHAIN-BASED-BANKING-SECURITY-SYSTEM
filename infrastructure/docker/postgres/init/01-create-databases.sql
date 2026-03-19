-- =============================================================================
-- Blockchain Banking Security System — PostgreSQL Initialization
-- Creates all per-service databases on first container start.
-- =============================================================================

SELECT 'CREATE DATABASE bbss_auth'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bbss_auth')\gexec

SELECT 'CREATE DATABASE bbss_tenant'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bbss_tenant')\gexec

SELECT 'CREATE DATABASE bbss_transaction'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bbss_transaction')\gexec

SELECT 'CREATE DATABASE bbss_audit'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bbss_audit')\gexec

SELECT 'CREATE DATABASE bbss_fraud'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bbss_fraud')\gexec

SELECT 'CREATE DATABASE bbss_blockchain'
    WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bbss_blockchain')\gexec
