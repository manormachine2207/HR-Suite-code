package io.github.manormachine2207.hrsuite.workflow;

import java.time.Instant;

/**
 * Engine-neutraler Blick auf einen offenen User-Task (ADR-013). {@code businessKey}
 * ist die Antrag-ID (gesetzt beim Instanz-Start), {@code taskDefinitionKey} der
 * Step-Key aus dem kompilierten BPMN.
 */
public record WorkflowTask(
        String id,
        String name,
        String taskDefinitionKey,
        String processInstanceId,
        String businessKey,
        Instant createdAt) {
}
