package io.github.manormachine2207.hrsuite.antragstyp.graph;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * SP1 Graph→BPMN-Compiler (ADR-012): kompiliert den validierten Freiform-Graphen zu
 * deploybarem Flowable-BPMN. XOR→exclusiveGateway (Kanten-Bedingungen→
 * conditionExpression, eine unkonditionierte Kante = default), AND→parallelGateway,
 * FORM/APPROVAL→userTask, ACTION→serviceTask (n8nActionDelegate). Knoten-Key =
 * BPMN-Element-ID. Pure Funktion — validiert via {@link GraphValidator} vor dem
 * Kompilieren und wirft {@link IllegalArgumentException} mit allen Issues.
 */
public final class GraphBpmnCompiler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GraphBpmnCompiler() {
    }

    public static String compile(String processKey, String processName, GraphDefinition graph) {
        List<String> issues = GraphValidator.validate(graph);
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException("graph is not compilable: " + String.join("; ", issues));
        }
        if (!GraphValidator.KEY_PATTERN.matcher(processKey).matches()) {
            throw new IllegalArgumentException("invalid processKey '" + processKey + "'");
        }

        // BPMN element ids: START -> "start", ENDs -> "end_0..n", others -> node key.
        Map<String, String> bpmnId = new HashMap<>();
        int endIndex = 0;
        for (GraphNode n : graph.nodes()) {
            switch (n.type()) {
                case START -> bpmnId.put(n.id(), "start");
                case END -> bpmnId.put(n.id(), "end_" + endIndex++);
                default -> bpmnId.put(n.id(), n.data().key());
            }
        }

        var elements = new ArrayList<String>();
        var seqFlows = new ArrayList<String>();

        // sequence flows first: XOR default flows must be known before emitting gateways
        Map<String, String> xorDefaultFlow = new HashMap<>();   // node id -> default flow id
        Set<String> usedFlowIds = new HashSet<>();
        for (GraphEdge e : graph.edges()) {
            String from = bpmnId.get(e.source());
            String to = bpmnId.get(e.target());
            String flowId = uniqueFlowId(usedFlowIds, "sf_" + from + "_" + to);
            GraphNode source = graph.nodes().stream()
                    .filter(n -> n.id().equals(e.source())).findFirst().orElseThrow();
            boolean unconditioned = e.condition() == null || e.condition().isBlank();
            if (source.type() == GraphNodeType.XOR && unconditioned) {
                xorDefaultFlow.put(source.id(), flowId);
            }
            String condition = unconditioned ? null : compileCondition(e.condition());
            seqFlows.add(sf(flowId, from, to, condition));
        }

        for (GraphNode n : graph.nodes()) {
            String id = bpmnId.get(n.id());
            switch (n.type()) {
                case START -> elements.add("<startEvent id=\"" + id + "\"/>");
                case END -> elements.add("<endEvent id=\"" + id + "\"/>");
                case FORM -> elements.add("""
                        <userTask id="%s" name="%s">
                          <documentation>FORM</documentation>
                        </userTask>""".formatted(id, lbl(n.data().title())));
                case APPROVAL -> {
                    String role = (n.data().assigneeRole() == null || n.data().assigneeRole().isBlank())
                            ? "hr-reviewer" : n.data().assigneeRole();
                    elements.add("<userTask id=\"%s\" name=\"%s\" flowable:candidateGroups=\"%s\"/>"
                            .formatted(id, lbl(n.data().title()), esc(role)));
                }
                case ACTION -> elements.add(actionTask(id, n.data()));
                case XOR -> {
                    String defaultAttr = xorDefaultFlow.containsKey(n.id())
                            ? " default=\"" + xorDefaultFlow.get(n.id()) + "\"" : "";
                    elements.add("<exclusiveGateway id=\"" + id + "\"" + defaultAttr + "/>");
                }
                case AND -> elements.add("<parallelGateway id=\"" + id + "\"/>");
            }
        }
        return buildBpmn(processKey, safeName(processName, processKey), elements, seqFlows);
    }

    /**
     * Compiles the constrained condition syntax ({@code var == 'value'}) to a JUEL
     * expression. Safe by construction: the validator admits only the closed pattern,
     * never free-form JUEL. Emitted null-safe via {@code execution.getVariable(...)}:
     * a bare {@code ${var == ...}} makes Flowable throw "Unknown property" when the
     * variable was never set — getVariable returns null instead, the condition is
     * false, and the token takes the gateway's default flow (deny-by-default).
     */
    private static String compileCondition(String condition) {
        Matcher m = GraphValidator.CONDITION_PATTERN.matcher(condition);
        if (!m.matches()) {
            throw new IllegalArgumentException("uncompilable condition: " + condition);
        }
        // group 3 = quoted string literal, group 4 = numeric literal (validator guarantees
        // ordering operators only ever pair with numbers). Numeric null-safety: a missing
        // variable coerces to 0 in EL arithmetic comparison -> false -> default flow.
        String literal = m.group(3) != null ? "'" + m.group(3) + "'" : m.group(4);
        return "${execution.getVariable('%s') %s %s}".formatted(m.group(1), m.group(2), literal);
    }

    private static String actionTask(String id, GraphNodeData data) {
        var ext = new StringBuilder();
        ext.append("""
                  <extensionElements>
                    <flowable:field name="ref">
                      <flowable:string>%s</flowable:string>
                    </flowable:field>""".formatted(esc(data.ref())));
        if (!data.inputMapping().isEmpty()) {
            try {
                String json = MAPPER.writeValueAsString(data.inputMapping());
                ext.append("""

                    <flowable:field name="inputMappingJson">
                      <flowable:string>%s</flowable:string>
                    </flowable:field>""".formatted(esc(json)));
            } catch (Exception e) {
                throw new IllegalStateException("Cannot serialize inputMapping for ACTION node: " + id, e);
            }
        }
        ext.append("\n                  </extensionElements>");
        return """
                <serviceTask id="%s" name="%s"
                             flowable:delegateExpression="${n8nActionDelegate}">
                %s
                </serviceTask>""".formatted(id, lbl(data.title()), ext);
    }

    private static String uniqueFlowId(Set<String> used, String base) {
        String id = base;
        int i = 1;
        while (!used.add(id)) {
            id = base + "_" + i++;
        }
        return id;
    }

    private static String sf(String id, String from, String to, String condition) {
        if (condition == null) {
            return "<sequenceFlow id=\"%s\" sourceRef=\"%s\" targetRef=\"%s\"/>".formatted(id, from, to);
        }
        return """
                <sequenceFlow id="%s" sourceRef="%s" targetRef="%s">
                  <conditionExpression xsi:type="tFormalExpression">%s</conditionExpression>
                </sequenceFlow>""".formatted(id, from, to, condition);
    }

    private static String lbl(Map<String, String> title) {
        if (title == null || title.isEmpty()) {
            return "";
        }
        return esc(title.getOrDefault("de", title.values().stream().findFirst().orElse("")));
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
