--liquibase formatted sql

--changeset hr-suite:004-create-antrag
--comment: antrag instance (tenant-scoped; RLS enabled in 005). ADR-009 version pin.
-- antragstyp_version_id + submitted_minor are NULL while DRAFT and set at submission
-- (a draft is not yet pinned, ADR-009 §1). antragsteller_subject holds the JWT subject
-- until the identity-sp cut introduces a User table (then it maps to User.id); this
-- mirrors how 0.4.0 left antragstyp_version.published_by unwired.
CREATE TABLE antrag (
    id                       uuid          NOT NULL,
    tenant_id                uuid          NOT NULL,
    antragstyp_id            uuid          NOT NULL,
    antragstyp_version_id    uuid,
    submitted_minor          int,
    migrated_from_version_id uuid,
    antragsteller_subject    varchar(256)  NOT NULL,
    status                   varchar(16)   NOT NULL,
    payload                  jsonb         NOT NULL DEFAULT '{}'::jsonb,
    workflow_process_id      varchar(256),
    submitted_at             timestamptz,
    decided_at               timestamptz,
    decision_comment         jsonb,
    created_at               timestamptz   NOT NULL,
    updated_at               timestamptz   NOT NULL,
    CONSTRAINT pk_antrag PRIMARY KEY (id),
    CONSTRAINT fk_antrag_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_antrag_antragstyp FOREIGN KEY (antragstyp_id) REFERENCES antragstyp (id),
    CONSTRAINT fk_antrag_version FOREIGN KEY (antragstyp_version_id) REFERENCES antragstyp_version (id),
    CONSTRAINT fk_antrag_migrated_from FOREIGN KEY (migrated_from_version_id) REFERENCES antragstyp_version (id),
    CONSTRAINT ck_antrag_status CHECK (status IN ('DRAFT','SUBMITTED','IN_REVIEW','APPROVED','REJECTED','CANCELLED','ESCALATED'))
);
CREATE INDEX ix_antrag_tenant_status ON antrag (tenant_id, status);
CREATE INDEX ix_antrag_tenant_subject ON antrag (tenant_id, antragsteller_subject);
--rollback DROP TABLE antrag;
