package io.github.manormachine2207.hrsuite.antragstyp;

/**
 * Festes, OSS-generisches Kategorie-Vokabular für Antragstypen (ADR-021). Schlüssel
 * sprachneutral; die i18n-Labels leben im Frontend (ngx-translate, Tenet 6). Keine
 * mandantenspezifischen Kategorien (YAGNI) — bei Bedarf späterer eigener Cut.
 */
public enum AntragsKategorie {
    ABSENCE,
    FINANCE,
    EMPLOYMENT,
    DEVELOPMENT,
    OTHER
}
