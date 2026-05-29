-- AntragsTypRlsIT fixture (ADR-008): provision the restricted runtime role.
--
-- RLS is bypassed by superuser/BYPASSRLS roles, and the Testcontainers bootstrap
-- user is a superuser (PostgreSQL forbids demoting it). So this script — run once
-- by Testcontainers' withInitScript() as that bootstrap user — creates a dedicated
-- NOSUPERUSER login role. The Spring datasource then connects as 'hrsuite_app' for
-- Liquibase AND runtime, so the tables are owned by a non-superuser and FORCE ROW
-- LEVEL SECURITY actually enforces tenant isolation. Mirrors docker/postgres-init/.
CREATE ROLE hrsuite_app WITH LOGIN PASSWORD 'dev' NOSUPERUSER NOBYPASSRLS;
GRANT CREATE, USAGE ON SCHEMA public TO hrsuite_app;
