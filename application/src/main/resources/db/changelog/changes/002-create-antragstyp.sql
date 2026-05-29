--liquibase formatted sql

--changeset hr-suite:002-create-antragstyp
--comment: antragstyp aggregate (tenant-scoped; RLS enabled in 003)
CREATE TABLE antragstyp (
    id                 uuid          NOT NULL,
    tenant_id          uuid          NOT NULL,
    key                varchar(128)  NOT NULL,
    title              jsonb         NOT NULL,
    description        jsonb,
    status             varchar(16)   NOT NULL,
    current_version_id uuid,
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    CONSTRAINT pk_antragstyp PRIMARY KEY (id),
    CONSTRAINT fk_antragstyp_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uq_antragstyp_tenant_key UNIQUE (tenant_id, key),
    CONSTRAINT ck_antragstyp_status CHECK (status IN ('DRAFT','LIVE','DEPRECATED','ARCHIVED'))
);
CREATE INDEX ix_antragstyp_tenant ON antragstyp (tenant_id);
--rollback DROP TABLE antragstyp;

--changeset hr-suite:002-create-antragstyp-version
--comment: antragstyp_version major snapshot (tenant-scoped; RLS enabled in 003)
CREATE TABLE antragstyp_version (
    id                         uuid          NOT NULL,
    tenant_id                  uuid          NOT NULL,
    antragstyp_id              uuid          NOT NULL,
    major                      int           NOT NULL,
    minor                      int           NOT NULL DEFAULT 0,
    status                     varchar(16)   NOT NULL,
    form_definition            jsonb         NOT NULL,
    workflow_bpmn              text,
    sf_action_bindings         jsonb,
    workflow_deployment_id     varchar(256),
    process_definition_key     varchar(256),
    process_definition_version int,
    minor_changelog            jsonb,
    published_at               timestamptz,
    published_by               uuid,
    created_at                 timestamptz   NOT NULL,
    updated_at                 timestamptz   NOT NULL,
    CONSTRAINT pk_antragstyp_version PRIMARY KEY (id),
    CONSTRAINT fk_atv_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_atv_antragstyp FOREIGN KEY (antragstyp_id) REFERENCES antragstyp (id),
    CONSTRAINT uq_atv_antragstyp_major UNIQUE (antragstyp_id, major),
    CONSTRAINT ck_atv_status CHECK (status IN ('DRAFT','PUBLISHED','DEPRECATED','ARCHIVED'))
);
CREATE INDEX ix_atv_tenant ON antragstyp_version (tenant_id);
CREATE INDEX ix_atv_antragstyp ON antragstyp_version (antragstyp_id);
--rollback DROP TABLE antragstyp_version;

--changeset hr-suite:002-antragstyp-current-version-fk
--comment: deferred circular FK antragstyp.current_version_id -> antragstyp_version.id
ALTER TABLE antragstyp
    ADD CONSTRAINT fk_antragstyp_current_version
    FOREIGN KEY (current_version_id) REFERENCES antragstyp_version (id);
--rollback ALTER TABLE antragstyp DROP CONSTRAINT fk_antragstyp_current_version;
