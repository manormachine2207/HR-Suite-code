--liquibase formatted sql

--changeset hr-suite:001-create-tenant
--comment: tenant aggregate (system root, intentionally OUTSIDE RLS per ADR-008)
CREATE TABLE tenant (
    id             uuid          NOT NULL,
    code           varchar(64)   NOT NULL,
    display_name   jsonb         NOT NULL,
    subdomain      varchar(64)   NOT NULL,
    status         varchar(16)   NOT NULL,
    default_locale varchar(5)    NOT NULL DEFAULT 'de',
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    CONSTRAINT pk_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_code UNIQUE (code),
    CONSTRAINT uq_tenant_subdomain UNIQUE (subdomain),
    CONSTRAINT ck_tenant_status CHECK (status IN ('ACTIVE','SUSPENDED','ONBOARDING','ARCHIVED'))
);
--rollback DROP TABLE tenant;
