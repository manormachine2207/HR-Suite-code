--liquibase formatted sql

--changeset hr-suite:014-add-antrag-email
--comment: ADR-017 Stufe 2b — capture the applicant's email (from the OIDC `email`
-- claim at submit) so a terminal decision can also send an e-mail via the SMTP
-- relay. Nullable: dev/legacy submissions without the claim stay in-app only.
ALTER TABLE antrag ADD COLUMN antragsteller_email varchar(320);
--rollback ALTER TABLE antrag DROP COLUMN antragsteller_email;
