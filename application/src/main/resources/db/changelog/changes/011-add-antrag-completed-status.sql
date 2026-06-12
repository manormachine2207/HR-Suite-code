--liquibase formatted sql

--changeset hr-suite:011-add-antrag-completed-status
--comment: ADR-013 Review-Pfad. Neuer terminaler Status COMPLETED fuer Prozesse, die
-- ohne APPROVAL-Outcome enden (ehrlicher als ein fingiertes APPROVED).
ALTER TABLE antrag DROP CONSTRAINT ck_antrag_status;
ALTER TABLE antrag ADD CONSTRAINT ck_antrag_status
    CHECK (status IN ('DRAFT','SUBMITTED','IN_REVIEW','APPROVED','REJECTED','COMPLETED','CANCELLED','ESCALATED'));
--rollback ALTER TABLE antrag DROP CONSTRAINT ck_antrag_status;
--rollback ALTER TABLE antrag ADD CONSTRAINT ck_antrag_status CHECK (status IN ('DRAFT','SUBMITTED','IN_REVIEW','APPROVED','REJECTED','CANCELLED','ESCALATED'));
