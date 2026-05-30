--liquibase formatted sql

--changeset hr-suite:007-create-action-execution
--comment: action execution record + dead-letter (ADR-010 L2). One row per
-- (process_instance_id, step_key) for idempotency; status walks
-- PENDING -> RUNNING -> SUCCEEDED | FAILED | DEAD. RLS enabled below.
CREATE TABLE action_execution (
    id                  uuid          NOT NULL,
    tenant_id           uuid          NOT NULL,
    process_instance_id varchar(256)  NOT NULL,
    step_key            varchar(128)  NOT NULL,
    ref                 varchar(128)  NOT NULL,
    status              varchar(16)   NOT NULL,
    attempts            int           NOT NULL DEFAULT 0,
    last_error          text,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    CONSTRAINT pk_action_execution PRIMARY KEY (id),
    CONSTRAINT fk_action_execution_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uq_action_execution_step UNIQUE (process_instance_id, step_key),
    CONSTRAINT ck_action_execution_status CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','DEAD'))
);
CREATE INDEX ix_action_execution_tenant_status ON action_execution (tenant_id, status);
--rollback DROP TABLE action_execution;

--changeset hr-suite:007b-enable-rls-action-execution
--comment: ADR-008 RLS, same pattern as 003/005/006.
ALTER TABLE action_execution ENABLE ROW LEVEL SECURITY;
ALTER TABLE action_execution FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON action_execution
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
--rollback DROP POLICY tenant_isolation ON action_execution;
--rollback ALTER TABLE action_execution NO FORCE ROW LEVEL SECURITY;
--rollback ALTER TABLE action_execution DISABLE ROW LEVEL SECURITY;
