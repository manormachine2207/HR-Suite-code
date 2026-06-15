--liquibase formatted sql

--changeset hr-suite:017-add-antragstyp-category
--comment: ADR-021 — feste Kategorie am Antragstyp (Service-Portal-Katalog). Nullable;
-- bestehende Typen bleiben leer und erscheinen im Katalog unter OTHER/"Sonstiges".
-- Werte sind das AntragsKategorie-Enum (ABSENCE/FINANCE/EMPLOYMENT/DEVELOPMENT/OTHER);
-- die Validierung erfolgt in der App (Jackson-Enum-Mapping), nicht als DB-CHECK, damit
-- neue Kategorien später ohne Migration ergänzt werden können.
ALTER TABLE antragstyp ADD COLUMN category varchar(32);
--rollback ALTER TABLE antragstyp DROP COLUMN category;
