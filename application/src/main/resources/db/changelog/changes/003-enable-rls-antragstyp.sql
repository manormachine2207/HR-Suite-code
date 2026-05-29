--liquibase formatted sql

--changeset hr-suite:003-enable-rls-antragstyp
--comment: ADR-008 RLS activation on the first tenant-scoped business tables
-- FORCE applies the policy even to the table owner (the app role owns these
-- tables), so no separate non-owner DB role is required. The policy reads the
-- per-transaction GUC app.tenant_id set by TenantContextAspect via set_config(..,
-- is_local=true). current_setting(.., true) returns NULL when unset (missing_ok);
-- NULLIF turns an empty string into NULL; a NULL comparison yields no rows
-- (deny-by-default) instead of erroring. WITH CHECK blocks inserting/updating
-- rows for a foreign tenant.
ALTER TABLE antragstyp ENABLE ROW LEVEL SECURITY;
ALTER TABLE antragstyp FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON antragstyp
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE antragstyp_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE antragstyp_version FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON antragstyp_version
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
--rollback DROP POLICY tenant_isolation ON antragstyp_version;
--rollback ALTER TABLE antragstyp_version NO FORCE ROW LEVEL SECURITY;
--rollback ALTER TABLE antragstyp_version DISABLE ROW LEVEL SECURITY;
--rollback DROP POLICY tenant_isolation ON antragstyp;
--rollback ALTER TABLE antragstyp NO FORCE ROW LEVEL SECURITY;
--rollback ALTER TABLE antragstyp DISABLE ROW LEVEL SECURITY;
