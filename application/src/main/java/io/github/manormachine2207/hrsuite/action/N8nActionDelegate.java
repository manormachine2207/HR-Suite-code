package io.github.manormachine2207.hrsuite.action;

import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * BPMN {@code serviceTask} bridge to the action layer (ADR-010 L2). Referenced from
 * BPMN as {@code flowable:delegateExpression="${n8nActionDelegate}"}. {@code ref} is
 * a Flowable field; {@code actionInput} is a process variable (a Map). The compiled
 * flow (Cut B) sets both; until then a test/seed sets them. On a terminal/dead action
 * it raises {@code BpmnError("ACTION_FAILED")} so the process can route an error path.
 */
@Component("n8nActionDelegate")
public class N8nActionDelegate implements JavaDelegate {

    private final ActionExecutionService actionExecutionService;
    private Expression ref;

    public N8nActionDelegate(ActionExecutionService actionExecutionService) {
        this.actionExecutionService = actionExecutionService;
    }

    public void setRef(Expression ref) {
        this.ref = ref;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(DelegateExecution execution) {
        String refValue = (String) ref.getValue(execution);
        String stepKey = execution.getCurrentActivityId();
        Object raw = execution.getVariable("actionInput");
        Map<String, Object> input = raw instanceof Map ? (Map<String, Object>) raw : Map.of();

        ActionExecution result = actionExecutionService.run(
                execution.getProcessInstanceId(), stepKey, refValue, input);

        execution.setVariable("actionStatus", result.getStatus().name());
        if (result.getStatus() == ActionStatus.DEAD || result.getStatus() == ActionStatus.FAILED) {
            throw new BpmnError("ACTION_FAILED", "action " + refValue + " ended " + result.getStatus());
        }
    }
}
