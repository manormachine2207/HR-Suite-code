package io.github.manormachine2207.hrsuite.antragstyp.form;

/**
 * Provisorischer Feldtyp-Katalog (siehe Decision-Draft DRAFT-form-definition-schema).
 * Bewusst minimal: deckt die in ADR-009 referenzierten Typ-Aenderungen ab.
 */
public enum FieldType {
    TEXT,
    NUMBER,
    DATE,
    BOOLEAN,
    SELECT,
    MULTI_SELECT
}
