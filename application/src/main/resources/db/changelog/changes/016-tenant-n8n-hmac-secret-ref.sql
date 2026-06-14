--liquibase formatted sql

--changeset hr-suite:016-tenant-n8n-hmac-secret-ref
--comment: SDR-004 — migrate tenant_n8n_config.hmac_secret (plaintext, Migration 006)
-- to the reference pattern: `hmac_secret_ref` holds the NAME of an environment
-- variable; the value is resolved at sign time via System.getenv (12-Factor), never
-- stored/logged/returned. This closes the documented SDR-004 legacy follow-up — the
-- exact FATIP lesson (plaintext secret later operated out of the DB). The plaintext
-- column is DROPPED; existing rows must be re-seeded with a ref (deployment provides
-- the env var). Nullable: a config without a configured ref is treated as
-- "not configured" by the connector (terminal, no signing with a null key).
ALTER TABLE tenant_n8n_config ADD COLUMN hmac_secret_ref varchar(128);
ALTER TABLE tenant_n8n_config DROP COLUMN hmac_secret;
--rollback ALTER TABLE tenant_n8n_config ADD COLUMN hmac_secret varchar(256) NOT NULL DEFAULT '';
--rollback ALTER TABLE tenant_n8n_config DROP COLUMN hmac_secret_ref;
