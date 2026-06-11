--liquibase formatted sql

--changeset hr-suite:010-add-action-business-key
--comment: stable idempotency anchor across process instances (Review 2026-06-12).
-- A resubmit after rollback starts a NEW Flowable process instance, so
-- (process_instance_id, step_key) cannot deduplicate side effects across submits.
-- business_key carries the Flowable business key (= antrag id); nullable for
-- non-antrag processes. Partial unique enforces one row per antrag+step.
ALTER TABLE action_execution ADD COLUMN business_key varchar(64);
CREATE UNIQUE INDEX uq_action_execution_business_step
    ON action_execution (tenant_id, business_key, step_key)
    WHERE business_key IS NOT NULL;
--rollback DROP INDEX uq_action_execution_business_step;
--rollback ALTER TABLE action_execution DROP COLUMN business_key;
