--liquibase formatted sql

--changeset hr-suite:018-create-email-outbox
--comment: ADR-017 Stufe 2c — async e-mail outbox. System-scope like smtp_relay_config
-- (NO RLS): this is a worker queue, no tenant API. tenant_id is stored as data so the
-- worker can hydrate locale / address per tenant, but no RLS policy is attached.
-- The worker polls by status (PENDING → SENT/FAILED/DEAD).
CREATE TABLE email_outbox (
    id               uuid          NOT NULL,
    tenant_id        uuid          NOT NULL,
    recipient_email  varchar(320)  NOT NULL,
    locale           varchar(5)    NOT NULL,
    template_key     varchar(64)   NOT NULL,
    params           jsonb         NOT NULL DEFAULT '{}'::jsonb,
    status           varchar(16)   NOT NULL DEFAULT 'PENDING',
    attempts         integer       NOT NULL DEFAULT 0,
    max_attempts     integer       NOT NULL DEFAULT 5,
    last_error       varchar(512),
    created_at       timestamptz   NOT NULL,
    last_attempt_at  timestamptz,
    sent_at          timestamptz,
    CONSTRAINT pk_email_outbox PRIMARY KEY (id),
    CONSTRAINT ck_email_outbox_status CHECK (status IN ('PENDING','SENT','FAILED','DEAD'))
);
CREATE INDEX email_outbox_status_idx ON email_outbox (status);
--rollback DROP TABLE email_outbox;

--changeset hr-suite:018b-add-antrag-locale
--comment: ADR-017 Stufe 2c — capture the applicant's preferred locale at submit so the
-- outbox worker can render the e-mail in the correct language. Nullable: legacy
-- submissions without the claim fall back to a platform default.
ALTER TABLE antrag ADD COLUMN antragsteller_locale varchar(5);
--rollback ALTER TABLE antrag DROP COLUMN antragsteller_locale;
