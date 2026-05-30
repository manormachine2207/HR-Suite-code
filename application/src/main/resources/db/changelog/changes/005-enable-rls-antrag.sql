--liquibase formatted sql

--changeset hr-suite:005-enable-rls-antrag
--comment: ADR-008 RLS on the antrag business table. Same FORCE + tenant_isolation
-- pattern as 003 (antragstyp): the app connects as the NOSUPERUSER 'hrsuite_app'
-- owner role, the policy reads the per-transaction GUC app.tenant_id set by
-- TenantContextAspect, and NULLIF(current_setting(.., true), '') yields NULL (=> no
-- rows) when the GUC is unset (deny-by-default).
ALTER TABLE antrag ENABLE ROW LEVEL SECURITY;
ALTER TABLE antrag FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON antrag
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
--rollback DROP POLICY tenant_isolation ON antrag;
--rollback ALTER TABLE antrag NO FORCE ROW LEVEL SECURITY;
--rollback ALTER TABLE antrag DISABLE ROW LEVEL SECURITY;
