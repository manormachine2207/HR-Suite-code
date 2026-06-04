--liquibase formatted sql

--changeset hr-suite:009-add-graph-definition
--comment: opaque free-form flow graph (ADR-012 SP2) on antragstyp_version. Stored as-is;
-- NOT compiled (the graph->BPMN compiler is SP1). Nullable: existing versions unaffected.
ALTER TABLE antragstyp_version
    ADD COLUMN IF NOT EXISTS graph_definition jsonb;
--rollback ALTER TABLE antragstyp_version DROP COLUMN IF EXISTS graph_definition;
