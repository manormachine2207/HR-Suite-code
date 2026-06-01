package io.github.manormachine2207.hrsuite.antragstyp.flow;

import java.util.List;

/**
 * Root of a low-code Antragstyp flow definition (DRAFT-ADR-010). An ordered list
 * of {@link FlowStep} that the {@link BpmnCompiler} translates to BPMN at publish time.
 * Stored as {@code flow_definition jsonb} on {@code antragstyp_version} (migration 008).
 */
public record FlowDefinition(List<FlowStep> steps) {
    public FlowDefinition {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
