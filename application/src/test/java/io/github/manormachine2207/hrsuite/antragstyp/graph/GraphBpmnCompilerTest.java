package io.github.manormachine2207.hrsuite.antragstyp.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SP1 Graph→BPMN-Compiler (ADR-012): XOR→exclusiveGateway, AND→parallelGateway,
 * Kanten→sequenceFlows mit conditionExpression; Knoten-Key = BPMN-Element-ID.
 * Pure Funktion; validiert vor dem Kompilieren.
 */
class GraphBpmnCompilerTest {

    private static final Map<String, String> T = Map.of("de", "Titel", "fr", "T", "it", "T", "en", "T");

    private static GraphNode node(String id, GraphNodeType type, String key) {
        return new GraphNode(id, type, new GraphNodeData(key, T, null, null, null));
    }

    private static GraphEdge edge(String id, String from, String to) {
        return new GraphEdge(id, from, to, null, null, null);
    }

    @Test
    void linearGraphCompilesToStartUserTaskEnd() {
        var g = new GraphDefinition(
                List.of(node("a", GraphNodeType.START, null),
                        node("b", GraphNodeType.FORM, "erfassen"),
                        node("c", GraphNodeType.END, null)),
                List.of(edge("e1", "a", "b"), edge("e2", "b", "c")));
        String bpmn = GraphBpmnCompiler.compile("proc_g1", "Graph", g);

        assertThat(bpmn).contains("<process id=\"proc_g1\"");
        assertThat(bpmn).contains("<startEvent id=\"start\"/>");
        assertThat(bpmn).contains("<userTask id=\"erfassen\"");
        assertThat(bpmn).contains("<endEvent id=\"end_0\"/>");
        assertThat(bpmn).contains("sourceRef=\"start\" targetRef=\"erfassen\"");
        assertThat(bpmn).contains("sourceRef=\"erfassen\" targetRef=\"end_0\"");
    }

    @Test
    void approvalCompilesToUserTaskWithCandidateGroup() {
        var approval = new GraphNode("b", GraphNodeType.APPROVAL,
                new GraphNodeData("freigabe", T, "tenant-admin", null, null));
        var g = new GraphDefinition(
                List.of(node("a", GraphNodeType.START, null), approval, node("c", GraphNodeType.END, null)),
                List.of(edge("e1", "a", "b"), edge("e2", "b", "c")));
        String bpmn = GraphBpmnCompiler.compile("proc_g2", "G", g);

        assertThat(bpmn).contains("<userTask id=\"freigabe\"");
        assertThat(bpmn).contains("flowable:candidateGroups=\"tenant-admin\"");
    }

    @Test
    void actionCompilesToServiceTaskWithDelegateRefAndInputMapping() {
        var action = new GraphNode("b", GraphNodeType.ACTION,
                new GraphNodeData("provision", T, null, "provision-ad-account", Map.of("upn", "x")));
        var g = new GraphDefinition(
                List.of(node("a", GraphNodeType.START, null), action, node("c", GraphNodeType.END, null)),
                List.of(edge("e1", "a", "b"), edge("e2", "b", "c")));
        String bpmn = GraphBpmnCompiler.compile("proc_g3", "G", g);

        assertThat(bpmn).contains("<serviceTask id=\"provision\"");
        assertThat(bpmn).contains("flowable:delegateExpression=\"${n8nActionDelegate}\"");
        assertThat(bpmn).contains("provision-ad-account");
        assertThat(bpmn).contains("inputMappingJson");
    }

    @Test
    void xorCompilesToExclusiveGatewayWithConditionsAndDefault() {
        var g = new GraphDefinition(
                List.of(node("a", GraphNodeType.START, null),
                        node("x", GraphNodeType.XOR, "entscheid"),
                        node("e1n", GraphNodeType.END, null),
                        node("e2n", GraphNodeType.END, null)),
                List.of(edge("e1", "a", "x"),
                        new GraphEdge("e2", "x", "e1n", null, "ja", "entscheid_outcome == 'approve'"),
                        edge("e3", "x", "e2n")));
        String bpmn = GraphBpmnCompiler.compile("proc_g4", "G", g);

        assertThat(bpmn).contains("<exclusiveGateway id=\"entscheid\"");
        // null-safe form: a missing variable must mean "false -> default flow",
        // not a Flowable "Unknown property" exception at complete time
        assertThat(bpmn).contains("${execution.getVariable('entscheid_outcome') == 'approve'}");
        // the unconditioned branch is the gateway default and stays unconditional
        assertThat(bpmn).containsPattern("default=\"sf_entscheid_end_[01]\"");
    }

    @Test
    void andCompilesToParallelGateways() {
        var g = new GraphDefinition(
                List.of(node("a", GraphNodeType.START, null),
                        node("split", GraphNodeType.AND, "fanout"),
                        node("t1", GraphNodeType.FORM, "links"),
                        node("t2", GraphNodeType.FORM, "rechts"),
                        node("join", GraphNodeType.AND, "fanin"),
                        node("z", GraphNodeType.END, null)),
                List.of(edge("e1", "a", "split"),
                        edge("e2", "split", "t1"), edge("e3", "split", "t2"),
                        edge("e4", "t1", "join"), edge("e5", "t2", "join"),
                        edge("e6", "join", "z")));
        String bpmn = GraphBpmnCompiler.compile("proc_g5", "G", g);

        assertThat(bpmn).contains("<parallelGateway id=\"fanout\"/>");
        assertThat(bpmn).contains("<parallelGateway id=\"fanin\"/>");
    }

    @Test
    void invalidGraphThrowsWithAllIssues() {
        var g = new GraphDefinition(
                List.of(node("a", GraphNodeType.FORM, "ohne_start")),
                List.of());
        assertThatThrownBy(() -> GraphBpmnCompiler.compile("proc_g6", "G", g))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("START");
    }

    @Test
    void jsonGraphFromFrontendShapeParsesAndCompiles() throws Exception {
        String json = """
                {"nodes":[
                   {"id":"n1","type":"START","position":{"x":0,"y":0},"data":{}},
                   {"id":"n2","type":"FORM","position":{"x":100,"y":0},
                    "data":{"key":"erfassen","title":{"de":"Erfassen"}}},
                   {"id":"n3","type":"END","position":{"x":200,"y":0},"data":{}}],
                 "edges":[
                   {"id":"e1","source":"n1","target":"n2"},
                   {"id":"e2","source":"n2","target":"n3","sourceHandle":"out"}]}
                """;
        var g = GraphDefinition.from(new ObjectMapper().readTree(json));
        String bpmn = GraphBpmnCompiler.compile("proc_g7", "G", g);
        assertThat(bpmn).contains("<userTask id=\"erfassen\"");
    }
}
