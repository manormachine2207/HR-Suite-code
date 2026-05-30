package io.github.manormachine2207.hrsuite.action;

import java.util.Map;
import java.util.UUID;

/** Input to an {@link ActionConnector}: which n8n workflow ({@code ref}) to run with {@code input}. */
public record ActionRequest(
        UUID tenantId,
        String processInstanceId,
        String stepKey,
        String ref,
        Map<String, Object> input) {
}
