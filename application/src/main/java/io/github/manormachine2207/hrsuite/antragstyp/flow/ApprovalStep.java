package io.github.manormachine2207.hrsuite.antragstyp.flow;

import java.util.List;
import java.util.Map;

/**
 * A human-approval step. {@code assigneeRole} is a Flowable candidate group.
 * {@code outcomes} lists decision values in order; the FIRST outcome is the
 * "continue" path (typically "approve"); remaining outcomes route to terminal end events.
 * Completers set the process variable {@code {key}_outcome} to one of these values.
 */
public record ApprovalStep(
        String key,
        Map<String, String> title,
        String assigneeRole,
        List<String> outcomes) implements FlowStep {

    public ApprovalStep {
        outcomes = (outcomes == null || outcomes.isEmpty())
                ? List.of("approve", "reject") : List.copyOf(outcomes);
    }
}
