package io.github.manormachine2207.hrsuite.action;

import java.util.Map;
import java.util.UUID;

/**
 * Input to an {@link ActionConnector}: which n8n workflow ({@code ref}) to run with
 * {@code input}. {@code businessKey} (= antrag id, nullable) anchors the idempotency
 * key across process instances.
 */
public record ActionRequest(
        UUID tenantId,
        String processInstanceId,
        String businessKey,
        String stepKey,
        String ref,
        Map<String, Object> input) {
}
