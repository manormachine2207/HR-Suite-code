package io.github.manormachine2207.hrsuite.antrag;

/**
 * Lifecycle of a concrete {@link Antrag} (03-Domain-Model). Applicant-side
 * transitions: DRAFT → SUBMITTED → CANCELLED. Review-side transitions (ADR-013):
 * SUBMITTED → IN_REVIEW (task completed, process still running) → APPROVED /
 * REJECTED (approval outcome) bzw. COMPLETED (process ended without an approval
 * outcome). ESCALATED is reserved for a later cut.
 */
public enum AntragStatus {
    DRAFT,
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    /** Process ended without an approval outcome (e.g. FORM-only or placeholder flows), ADR-013. */
    COMPLETED,
    CANCELLED,
    ESCALATED
}
