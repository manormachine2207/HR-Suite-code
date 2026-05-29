package io.github.manormachine2207.hrsuite.antragstyp;

/** Lifecycle of a single major version (ADR-009 §3). */
public enum VersionStatus {
    DRAFT,       // editable, never instantiated
    PUBLISHED,   // live; exactly one per AntragsTyp; current_version_id points here
    DEPRECATED,  // no new submissions; in-flight drain (minor fixes still allowed)
    ARCHIVED     // no instances left
}
