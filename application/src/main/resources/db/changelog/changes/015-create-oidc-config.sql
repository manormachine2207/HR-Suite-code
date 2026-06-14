--liquibase formatted sql

--changeset hr-suite:015-create-oidc-config
--comment: ADR-019 Stufe 2 — global OIDC config (singleton). System-scope like
-- `smtp_relay_config` / `tenant` (NO RLS): one platform-wide identity provider,
-- platform-admin-guarded at the API (SDR-001: SSO is global, not per tenant).
-- SDR-004: the client secret is NEVER stored — `client_secret_ref` holds the NAME
-- of an environment variable, resolved at runtime. `enabled` is stored but NOT yet
-- enforced (configure-only; live enforcement is the Stufe-2b follow-up). A default
-- disabled row (id=1) is seeded so GET always returns a config.
CREATE TABLE oidc_config (
    id                smallint     NOT NULL DEFAULT 1,
    issuer            varchar(512),
    client_id         varchar(255),
    client_secret_ref varchar(128),
    scopes            varchar(512) DEFAULT 'openid profile email',
    redirect_uri      varchar(512),
    tenant_claim      varchar(128),
    enabled           boolean      NOT NULL DEFAULT false,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    CONSTRAINT pk_oidc_config PRIMARY KEY (id),
    CONSTRAINT ck_oidc_config_singleton CHECK (id = 1)
);
INSERT INTO oidc_config (id, created_at, updated_at) VALUES (1, now(), now());
--rollback DROP TABLE oidc_config;
