package io.github.manormachine2207.hrsuite.antrag;

/**
 * Lifecycle of a concrete {@link Antrag} (03-Domain-Model). This cut implements the
 * applicant-side transitions DRAFT → SUBMITTED → CANCELLED; the review/approval
 * states (IN_REVIEW, APPROVED, REJECTED, ESCALATED) are part of the workflow-engine
 * cut and are listed here so the DB CHECK constraint and the type are stable.
 */
public enum AntragStatus {
    DRAFT,
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    CANCELLED,
    ESCALATED
}
