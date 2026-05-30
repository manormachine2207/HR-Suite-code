package io.github.manormachine2207.hrsuite.antrag.dto;

import java.util.Map;

/** Replace the form answers of a still-unsubmitted DRAFT request. */
public record EditDraftRequest(
        Map<String, Object> payload
) {
}
