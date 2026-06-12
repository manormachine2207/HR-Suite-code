package io.github.manormachine2207.hrsuite.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Offener Review-Task inkl. Antrags-Kontext (ADR-013). {@code payload} ist nur im
 * Detail-Endpoint gesetzt; {@code allowedOutcomes} ist leer, wenn der Schritt keine
 * Outcomes deklariert (dann genuegt das sichere Pattern beim Complete).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskResponse(
        String taskId,
        String name,
        String stepKey,
        Instant createdAt,
        UUID antragId,
        String antragStatus,
        Map<String, String> antragstypTitle,
        String antragstellerSubject,
        List<String> allowedOutcomes,
        Map<String, Object> payload) {
}
