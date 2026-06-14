--liquibase formatted sql

--changeset hr-suite:013-create-notification
--comment: ADR-017 Stufe 2 — in-app notifications. tenant-scoped (RLS, ADR-008),
-- one row per recipient + event. `params` is a small jsonb bag rendered by the FE
-- via i18n (BDR-005); `antrag_id` is the deep-link target. read_at NULL = unread.
CREATE TABLE notification (
    id               uuid        NOT NULL,
    tenant_id        uuid        NOT NULL,
    recipient_subject text       NOT NULL,
    type             varchar(64) NOT NULL,
    antrag_id        uuid,
    params           jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at       timestamptz NOT NULL,
    read_at          timestamptz,
    CONSTRAINT pk_notification PRIMARY KEY (id)
);
CREATE INDEX notification_recipient_idx
    ON notification (tenant_id, recipient_subject, read_at, created_at DESC);
--rollback DROP TABLE notification;

--changeset hr-suite:013b-enable-rls-notification
--comment: ADR-008 RLS, same FORCE + tenant_isolation pattern as 005 (antrag).
ALTER TABLE notification ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
--rollback DROP POLICY tenant_isolation ON notification;
--rollback ALTER TABLE notification NO FORCE ROW LEVEL SECURITY;
--rollback ALTER TABLE notification DISABLE ROW LEVEL SECURITY;
