package io.github.manormachine2207.hrsuite.antragstyp.flow;

import java.util.List;
import java.util.Map;

/**
 * A conditional branch (exclusive gateway). The process variable
 * {@code conditionVariable} is compared to {@code approveValue}:
 * matching → {@code thenSteps}, otherwise → {@code elseSteps}.
 * Both paths join at a merge gateway before continuing.
 *
 * <p><strong>Note (Cut C):</strong> BRANCH compilation in {@link BpmnCompiler}
 * is not yet implemented and throws {@link UnsupportedOperationException}.
 * The model is complete so definitions can be stored and round-tripped.
 */
public record BranchStep(
        String key,
        Map<String, String> title,
        String conditionVariable,
        String approveValue,
        List<FlowStep> thenSteps,
        List<FlowStep> elseSteps) implements FlowStep {

    public BranchStep {
        thenSteps = thenSteps == null ? List.of() : List.copyOf(thenSteps);
        elseSteps = elseSteps == null ? List.of() : List.copyOf(elseSteps);
    }
}
