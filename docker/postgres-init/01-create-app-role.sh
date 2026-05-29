#!/bin/sh
# 01-create-app-role.sh — provision the restricted runtime DB role (ADR-008 RLS).
#
# The application MUST NOT connect as the Postgres bootstrap superuser. Superuser
# and BYPASSRLS roles bypass Row-Level Security unconditionally: FORCE ROW LEVEL
# SECURITY only binds a table's *owner*, never a superuser. The official postgres
# image creates POSTGRES_USER as the bootstrap superuser, and PostgreSQL forbids
# removing the SUPERUSER attribute from that role — so we cannot simply demote it.
#
# Instead we create a dedicated NOSUPERUSER login role here. The backend connects
# as this role for BOTH Liquibase migrations and runtime queries, so every table it
# creates is owned by a non-superuser and the tenant_isolation policies are enforced.
# Managed Postgres (RDS/Cloud SQL) already hands you such a non-superuser role; this
# script makes the local dev stack faithful to that production reality.
#
# Runs once, on first cluster initialisation (empty data dir), as POSTGRES_USER.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
CREATE ROLE hrsuite_app WITH LOGIN PASSWORD '${APP_DB_PASSWORD:-dev}'
    NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
GRANT CREATE, USAGE ON SCHEMA public TO hrsuite_app;
EOSQL

echo "[init] created restricted runtime role 'hrsuite_app' (NOSUPERUSER, owns app schema)"
