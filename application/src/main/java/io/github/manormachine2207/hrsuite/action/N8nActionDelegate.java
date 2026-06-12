package io.github.manormachine2207.hrsuite.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        input = resolvePlaceholders(input, execution);

        ActionExecution result = actionExecutionService.run(
                execution.getProcessInstanceId(), execution.getProcessInstanceBusinessKey(),
                stepKey, refValue, input);

        execution.setVariable("actionStatus", result.getStatus().name());
        if (result.getStatus() == ActionStatus.DEAD || result.getStatus() == ActionStatus.FAILED) {
            throw new BpmnError("ACTION_FAILED", "action " + refValue + " ended " + result.getStatus());
        }
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([A-Za-z][A-Za-z0-9_]*)\\}$");

    /**
     * Mapping-Werte der Form {@code ${var}} (ganzer Wert = genau ein Variablenname)
     * werden gegen Prozessvariablen aufgeloest — der Wert behaelt seinen Typ. Bewusst
     * eine geschlossene Syntax, KEIN JUEL (Tenet 1; das BPMN-Feld ist ein
     * {@code flowable:string}-Literal, die Engine evaluiert hier nichts): alles, was
     * nicht exakt dem Muster entspricht, geht literal durch. Fehlende Variablen werden
     * weggelassen statt {@code null} zu senden (Datenminimierung, Kap. 12.2).
     */
    private static Map<String, Object> resolvePlaceholders(Map<String, Object> input,
                                                           DelegateExecution execution) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            if (entry.getValue() instanceof String s) {
                Matcher m = PLACEHOLDER.matcher(s);
                if (m.matches()) {
                    Object value = execution.getVariable(m.group(1));
                    if (value != null) {
                        resolved.put(entry.getKey(), value);
                    }
                    continue;
                }
            }
            resolved.put(entry.getKey(), entry.getValue());
        }
        return resolved;
    }
}
