package io.github.manormachine2207.hrsuite.antragstyp.flow;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Translates a {@link FlowDefinition} to deployable Flowable BPMN 2.0 XML.
 * Pure function — no Spring context, safe to unit-test directly.
 *
 * <p>Supported step types: FORM (userTask), APPROVAL (userTask + exclusive gateway),
 * ACTION (serviceTask → n8nActionDelegate with optional inputMappingJson field).
 * BRANCH is modelled but throws {@link UnsupportedOperationException} (Cut C).
 */
public final class BpmnCompiler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final java.util.regex.Pattern KEY_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    private BpmnCompiler() {
    }

    private static String validateKey(String key, String role) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "Invalid " + role + " '" + key + "': must match [A-Za-z][A-Za-z0-9_]* "
                    + "(used as a BPMN element id and a JUEL variable name; hyphens/spaces/symbols are not allowed).");
        }
        return key;
    }

    /**
     * Compiles {@code flow} into a deployable BPMN process with id {@code processKey}.
     *
     * @throws IllegalArgumentException      if flow is null or has no steps
     * @throws UnsupportedOperationException if any step is a {@link BranchStep} (Cut C)
     */
    public static String compile(String processKey, String processName, FlowDefinition flow) {
        if (flow == null || flow.steps().isEmpty()) {
            throw new IllegalArgumentException(
                    "FlowDefinition must have at least one step to compile; processKey=" + processKey);
        }
        validateKey(processKey, "processKey");
        for (FlowStep step : flow.steps()) {
            validateKey(step.key(), "step key");
        }
        var elements = new ArrayList<String>();
        var seqFlows = new ArrayList<String>();

        elements.add("<startEvent id=\"start\"/>");

        List<FlowStep> steps = flow.steps();
        for (int i = 0; i < steps.size(); i++) {
            FlowStep step = steps.get(i);
            String from = (i == 0) ? "start" : exitId(steps.get(i - 1));
            // 'to' is used by APPROVAL for its gateway outgoing flows; FORM and ACTION
            // do NOT emit their own outgoing flow to avoid duplicate sequenceFlow IDs
            // (the next step emits its own incoming, covering the same connection).
            String to = (i < steps.size() - 1) ? entryId(steps.get(i + 1)) : "end";
            // An APPROVAL's exclusive gateway already emits the conditional flow to the
            // following step (the "continue" outflow).  If we also emitted an unconditional
            // incoming flow here, the gateway would have two outgoing flows to the same
            // target — one conditional and one unconditional — which is a BPMN semantic
            // defect.  So a step must only emit its incoming flow when its predecessor is
            // NOT an ApprovalStep.
            boolean emitIncoming = (i == 0) || !(steps.get(i - 1) instanceof ApprovalStep);
            compileStep(step, from, to, emitIncoming, elements, seqFlows);
        }

        // Sequence flow from the last step's exit node to the main end event.
        // FORM and ACTION emit only their INCOMING flow; this trailer closes the chain.
        // APPROVAL already handles all its own outgoing flows (via its exclusive gateway),
        // so no trailer is needed when APPROVAL is last.
        FlowStep lastStep = steps.get(steps.size() - 1);
        if (!(lastStep instanceof ApprovalStep)) {
            seqFlows.add(sf("sf_" + exitId(lastStep) + "_end", exitId(lastStep), "end", null));
        }

        elements.add("<endEvent id=\"end\"/>");
        return buildBpmn(processKey, safeName(processName, processKey), elements, seqFlows);
    }

    // --- entry / exit IDs ---

    private static String entryId(FlowStep step) {
        return step.key(); // first BPMN element always uses the step key as ID
    }

    private static String exitId(FlowStep step) {
        // APPROVAL exits via its exclusive gateway; all others exit via the step element itself
        return (step instanceof ApprovalStep) ? "gw_" + step.key() : step.key();
    }

    // --- step compilers ---

    private static void compileStep(FlowStep step, String from, String to,
                                     boolean emitIncoming,
                                     List<String> elements, List<String> seqFlows) {
        switch (step) {
            case FormStep s     -> compileForm(s, from, to, emitIncoming, elements, seqFlows);
            case ApprovalStep s -> compileApproval(s, from, to, emitIncoming, elements, seqFlows);
            case ActionStep s   -> compileAction(s, from, to, emitIncoming, elements, seqFlows);
            case BranchStep s   -> throw new UnsupportedOperationException(
                    "BRANCH step compilation is not yet supported (scheduled for Cut C): key=" + s.key());
        }
    }

    private static void compileForm(FormStep s, String from, String to,
                                     boolean emitIncoming,
                                     List<String> elements, List<String> seqFlows) {
        elements.add("""
                <userTask id="%s" name="%s">
                  <documentation>FORM</documentation>
                </userTask>""".formatted(s.key(), lbl(s.title())));
        // Only emit the INCOMING flow when the predecessor is not an ApprovalStep.
        // An ApprovalStep's exclusive gateway already emits the conditional "continue"
        // flow to this step, so no additional unconditional incoming flow is needed.
        if (emitIncoming) {
            seqFlows.add(sf("sf_" + from + "_" + s.key(), from, s.key(), null));
        }
    }

    private static void compileApproval(ApprovalStep s, String from, String to,
                                         boolean emitIncoming,
                                         List<String> elements, List<String> seqFlows) {
        String gwId = "gw_" + s.key();
        String role = (s.assigneeRole() == null || s.assigneeRole().isBlank())
                ? "hr-reviewer" : s.assigneeRole();

        // The reviewer's userTask
        elements.add("<userTask id=\"%s\" name=\"%s\" flowable:candidateGroups=\"%s\"/>"
                .formatted(s.key(), lbl(s.title()), esc(role)));
        // Only emit the incoming flow when the predecessor is not an ApprovalStep.
        if (emitIncoming) {
            seqFlows.add(sf("sf_" + from + "_" + s.key(), from, s.key(), null));
        }

        // Exclusive gateway — no 'default' attr; all outgoing flows have conditionExpressions
        elements.add("<exclusiveGateway id=\"%s\" name=\"%s_decision\"/>"
                .formatted(gwId, s.key()));
        seqFlows.add(sf("sf_" + s.key() + "_" + gwId, s.key(), gwId, null));

        // First outcome → continue to next step
        String mainOutcome = s.outcomes().get(0);
        seqFlows.add(sf("sf_" + gwId + "_continue", gwId, to,
                "${%s_outcome == '%s'}".formatted(s.key(), mainOutcome)));

        // Remaining outcomes → dedicated end events (terminal, e.g. reject)
        for (int i = 1; i < s.outcomes().size(); i++) {
            String outcome = s.outcomes().get(i);
            String endId = "end_" + s.key() + "_" + outcome;
            seqFlows.add(sf("sf_" + gwId + "_" + outcome, gwId, endId,
                    "${%s_outcome == '%s'}".formatted(s.key(), outcome)));
            elements.add("<endEvent id=\"" + endId + "\"/>");
        }
    }

    private static void compileAction(ActionStep s, String from, String to,
                                       boolean emitIncoming,
                                       List<String> elements, List<String> seqFlows) {
        var ext = new StringBuilder();
        ext.append("""
                  <extensionElements>
                    <flowable:field name="ref">
                      <flowable:string>%s</flowable:string>
                    </flowable:field>""".formatted(esc(s.ref())));

        if (!s.inputMapping().isEmpty()) {
            try {
                String json = MAPPER.writeValueAsString(s.inputMapping());
                ext.append("""

                    <flowable:field name="inputMappingJson">
                      <flowable:string>%s</flowable:string>
                    </flowable:field>""".formatted(esc(json)));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Cannot serialize inputMapping for ACTION step: " + s.key(), e);
            }
        }
        ext.append("\n                  </extensionElements>");

        elements.add("""
                <serviceTask id="%s" name="%s"
                             flowable:delegateExpression="${n8nActionDelegate}">
                %s
                </serviceTask>""".formatted(s.key(), lbl(s.title()), ext));
        // Only emit the INCOMING flow when the predecessor is not an ApprovalStep.
        // An ApprovalStep's exclusive gateway already emits the conditional "continue"
        // flow to this step, so no additional unconditional incoming flow is needed.
        if (emitIncoming) {
            seqFlows.add(sf("sf_" + from + "_" + s.key(), from, s.key(), null));
        }
    }

    // --- BPMN helpers ---

    private static String sf(String id, String from, String to, String condition) {
        if (condition == null) {
            return "<sequenceFlow id=\"%s\" sourceRef=\"%s\" targetRef=\"%s\"/>"
                    .formatted(id, from, to);
        }
        return """
                <sequenceFlow id="%s" sourceRef="%s" targetRef="%s">
                  <conditionExpression xsi:type="tFormalExpression">%s</conditionExpression>
                </sequenceFlow>""".formatted(id, from, to, condition);
    }

    private static String lbl(Map<String, String> title) {
        if (title == null || title.isEmpty()) return "";
        String v = title.getOrDefault("de",
                title.values().stream().findFirst().orElse(""));
        return esc(v);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String safeName(String name, String fallback) {
        return (name == null || name.isBlank()) ? fallback : esc(name);
    }

    private static String buildBpmn(String processKey, String processName,
                                     List<String> elements, List<String> seqFlows) {
        var sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://hr-suite/processes">
                  <process id="%s" name="%s" isExecutable="true">
                """.formatted(processKey, processName));
        for (String el : elements) {
            sb.append("    ").append(el.strip()).append("\n");
        }
        for (String f : seqFlows) {
            sb.append("    ").append(f.strip()).append("\n");
        }
        sb.append("  </process>\n</definitions>\n");
        return sb.toString();
    }
}
