package io.github.manormachine2207.hrsuite.antragstyp;

/** Lifecycle of the request-type definition as a whole (ADR-009 §3, 03-Domain-Model). */
public enum AntragsTypStatus {
    DRAFT,       // created, no published major yet
    LIVE,        // has a published major (current_version_id set)
    DEPRECATED,  // no new submissions; running drain
    ARCHIVED     // historical
}
