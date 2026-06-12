package io.github.manormachine2207.hrsuite.workflow;

import java.time.Instant;
import java.util.Set;

/**
 * Engine-neutraler Blick auf einen offenen User-Task (ADR-013). {@code businessKey}
 * ist die Antrag-ID (gesetzt beim Instanz-Start), {@code taskDefinitionKey} der
 * Step-Key aus dem kompilierten BPMN. {@code candidateGroups} sind die
 * Genehmiger-Gruppen des Tasks (ADR-016) — leer fuer gruppenlose FORM-Tasks.
 */
public record WorkflowTask(
        String id,
        String name,
        String taskDefinitionKey,
        String processInstanceId,
        String businessKey,
        Instant createdAt,
        Set<String> candidateGroups) {

    public WorkflowTask {
        candidateGroups = candidateGroups == null ? Set.of() : Set.copyOf(candidateGroups);
    }
}
