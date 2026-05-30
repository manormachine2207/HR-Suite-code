--liquibase formatted sql

--changeset hr-suite:006-create-tenant-n8n-config
--comment: per-tenant n8n endpoint config (ADR-010 L2). base_url + hmac_secret + an
-- allowlist of webhook refs the tenant may invoke. Secret is config, never code
-- (ADR-002). RLS enabled below.
CREATE TABLE tenant_n8n_config (
    tenant_id    uuid          NOT NULL,
    base_url     varchar(512)  NOT NULL,
    hmac_secret  varchar(256)  NOT NULL,
    allowed_refs jsonb         NOT NULL DEFAULT '[]'::jsonb,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    CONSTRAINT pk_tenant_n8n_config PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_n8n_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);
--rollback DROP TABLE tenant_n8n_config;

--changeset hr-suite:006b-enable-rls-tenant-n8n-config
--comment: ADR-008 RLS, same FORCE + tenant_isolation pattern as 003/005.
ALTER TABLE tenant_n8n_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_n8n_config FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenant_n8n_config
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
--rollback DROP POLICY tenant_isolation ON tenant_n8n_config;
--rollback ALTER TABLE tenant_n8n_config NO FORCE ROW LEVEL SECURITY;
--rollback ALTER TABLE tenant_n8n_config DISABLE ROW LEVEL SECURITY;
