--liquibase formatted sql

--changeset hr-suite:008-add-flow-definition
--comment: low-code FlowDefinition on antragstyp_version (DRAFT-ADR-010 Cut B).
-- Nullable: existing versions continue to work (workflowBpmn path unchanged).
-- At publish(), AntragsTypService compiles flow_definition to BPMN if non-null.
ALTER TABLE antragstyp_version
    ADD COLUMN IF NOT EXISTS flow_definition jsonb;
--rollback ALTER TABLE antragstyp_version DROP COLUMN IF EXISTS flow_definition;
