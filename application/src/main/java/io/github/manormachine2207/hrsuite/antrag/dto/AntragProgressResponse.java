package io.github.manormachine2207.hrsuite.antrag.dto;

import io.github.manormachine2207.hrsuite.workflow.WorkflowStepInfo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Workflow-Fortschritt eines Antrags fuer den Genehmigungsketten-Stepper (Cut D).
 * {@code workflowProcessId} dient als Trace-Referenz in der Detailansicht.
 */
public record AntragProgressResponse(
        UUID antragId,
        String workflowProcessId,
        List<Step> steps) {

    public record Step(
            String stepKey,
            String name,
            boolean completed,
            Instant startedAt,
            Instant completedAt) {

        static Step from(WorkflowStepInfo info) {
            return new Step(info.stepKey(), info.name(), info.completed(),
                    info.startedAt(), info.completedAt());
        }
    }

    public static AntragProgressResponse from(UUID antragId, String workflowProcessId,
                                              List<WorkflowStepInfo> steps) {
        return new AntragProgressResponse(antragId, workflowProcessId,
                steps.stream().map(Step::from).toList());
    }
}
