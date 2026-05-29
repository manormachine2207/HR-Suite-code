--liquibase formatted sql

--changeset hr-suite:003-enable-rls-antragstyp
--comment: ADR-008 RLS activation on the first tenant-scoped business tables
-- FORCE makes the policy apply to the table OWNER too (owners are exempt by
-- default). It does NOT, however, constrain superuser or BYPASSRLS roles -- those
-- bypass RLS unconditionally. The application must therefore connect as a role that
-- (a) owns these tables and (b) is NOT a superuser/BYPASSRLS role. The Postgres
-- bootstrap user (POSTGRES_USER) is a superuser and cannot be demoted, so the app
-- runs as a dedicated NOSUPERUSER role 'hrsuite_app', provisioned per environment
-- (docker/postgres-init/ for compose, withInitScript for the IT, a managed role in
-- prod). The policy reads the per-transaction GUC app.tenant_id set by
-- TenantContextAspect via set_config(.., is_local=true). current_setting(.., true)
-- returns NULL when unset (missing_ok);
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
