--liquibase formatted sql

--changeset hr-suite:012-create-smtp-relay-config
--comment: ADR-019 Stufe 3 — global SMTP relay config (singleton). System-scope like
-- `tenant` (NO RLS): a single platform-wide relay, platform-admin-guarded at the API.
-- SDR-004: the SMTP password is NEVER stored here — `password_ref` holds the NAME of
-- an environment variable, resolved at send time. A default disabled row (id=1) is
-- seeded so GET always returns a config.
CREATE TABLE smtp_relay_config (
    id                smallint     NOT NULL DEFAULT 1,
    host              varchar(255) NOT NULL DEFAULT 'mailpit',
    port              integer      NOT NULL DEFAULT 1025,
    security          varchar(16)  NOT NULL DEFAULT 'NONE',
    from_address      varchar(320) NOT NULL DEFAULT 'no-reply@hr-suite.local',
    from_name         varchar(255),
    username          varchar(255),
    password_ref      varchar(128),
    enabled           boolean      NOT NULL DEFAULT false,
    verified          boolean      NOT NULL DEFAULT false,
    last_test_at      timestamptz,
    last_test_outcome varchar(512),
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    CONSTRAINT pk_smtp_relay_config PRIMARY KEY (id),
    CONSTRAINT ck_smtp_relay_singleton CHECK (id = 1),
    CONSTRAINT ck_smtp_relay_security CHECK (security IN ('NONE','STARTTLS','TLS')),
    CONSTRAINT ck_smtp_relay_from_at CHECK (from_address LIKE '%@%'),
    CONSTRAINT ck_smtp_relay_port CHECK (port BETWEEN 1 AND 65535)
);
INSERT INTO smtp_relay_config (id, created_at, updated_at) VALUES (1, now(), now());
--rollback DROP TABLE smtp_relay_config;
