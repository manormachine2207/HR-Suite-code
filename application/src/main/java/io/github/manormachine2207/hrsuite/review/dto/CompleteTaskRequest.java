package io.github.manormachine2207.hrsuite.review.dto;

/**
 * Complete-Eingabe (ADR-013). {@code outcome} ist optional (FORM-/Platzhalter-Tasks
 * haben keins); {@code comment} wird als strukturiertes Log auditiert (SDR-002-Minimum).
 */
public record CompleteTaskRequest(
        String outcome,
        String comment) {
}
