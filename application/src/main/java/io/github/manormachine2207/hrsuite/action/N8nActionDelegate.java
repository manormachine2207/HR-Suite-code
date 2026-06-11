package io.github.manormachine2207.hrsuite.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(N8nActionDelegate.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ActionExecutionService actionExecutionService;
    private Expression ref;
    private Expression inputMappingJson;  // optional; set by compiled BPMN ACTION steps

    public N8nActionDelegate(ActionExecutionService actionExecutionService) {
        this.actionExecutionService = actionExecutionService;
    }

    public void setRef(Expression ref) {
        this.ref = ref;
    }

    public void setInputMappingJson(Expression inputMappingJson) {
        this.inputMappingJson = inputMappingJson;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String refValue = (String) ref.getValue(execution);
        String stepKey = execution.getCurrentActivityId();
        Object raw = execution.getVariable("actionInput");
        // Fall back to the compiled inputMappingJson field when actionInput is not set as a process variable
        if (raw == null && inputMappingJson != null) {
            String json = (String) inputMappingJson.getValue(execution);
            if (json != null && !json.isBlank()) {
                try {
                    raw = MAPPER.readValue(json, Map.class);
                } catch (Exception e) {
                    // Malformed inputMappingJson is a misconfiguration/tampering: silently proceeding
                    // with empty input could "succeed" with wrong/no data, which is unacceptable.
                    // Fail loud on the same terminal path as any other ACTION_FAILED outcome.
                    log.error("Malformed inputMappingJson for step {}: {}", stepKey, e.getMessage());
                    throw new BpmnError("ACTION_FAILED",
                            "malformed inputMappingJson for step " + stepKey + ": " + e.getMessage());
                }
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> input = raw instanceof Map ? (Map<String, Object>) raw : Map.of();

        ActionExecution result = actionExecutionService.run(
                execution.getProcessInstanceId(), execution.getProcessInstanceBusinessKey(),
                stepKey, refValue, input);

        execution.setVariable("actionStatus", result.getStatus().name());
        if (result.getStatus() == ActionStatus.DEAD || result.getStatus() == ActionStatus.FAILED) {
            throw new BpmnError("ACTION_FAILED", "action " + refValue + " ended " + result.getStatus());
        }
    }
}
