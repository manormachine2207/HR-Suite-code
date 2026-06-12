package io.github.manormachine2207.hrsuite.antragstyp.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SP1-Voll-Validierung (ADR-012): genau 1 START, >=1 END, Erreichbarkeit, keine
 * Sackgassen, Key-Disziplin, XOR-Bedingungen, saubere AND-Knoten. Pure Funktion.
 */
class GraphValidatorTest {

    private static final Map<String, String> T = Map.of("de", "T", "fr", "T", "it", "T", "en", "T");

    private static GraphNode start() {
        return new GraphNode("n_start", GraphNodeType.START, new GraphNodeData(null, null, null, null, null));
    }

    private static GraphNode end(String id) {
        return new GraphNode(id, GraphNodeType.END, new GraphNodeData(null, null, null, null, null));
    }

    private static GraphNode form(String id, String key) {
        return new GraphNode(id, GraphNodeType.FORM, new GraphNodeData(key, T, null, null, null));
    }

    private static GraphNode action(String id, String key, String ref) {
        return new GraphNode(id, GraphNodeType.ACTION, new GraphNodeData(key, T, null, ref, Map.of()));
    }

    private static GraphNode xor(String id, String key) {
        return new GraphNode(id, GraphNodeType.XOR, new GraphNodeData(key, T, null, null, null));
    }

    private static GraphEdge edge(String id, String from, String to) {
        return new GraphEdge(id, from, to, null, null, null);
    }

    private static GraphEdge cond(String id, String from, String to, String condition) {
        return new GraphEdge(id, from, to, null, null, condition);
    }

    @Test
    void minimalLinearGraphIsValid() {
        var g = new GraphDefinition(
                List.of(start(), form("n1", "erfassen"), end("n_end")),
                List.of(edge("e1", "n_start", "n1"), edge("e2", "n1", "n_end")));
        assertThat(GraphValidator.validate(g)).isEmpty();
    }

    @Test
    void missingStartIsReported() {
        var g = new GraphDefinition(
                List.of(form("n1", "erfassen"), end("n_end")),
                List.of(edge("e1", "n1", "n_end")));
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("START"));
    }

    @Test
    void twoStartsAreReported() {
        var g = new GraphDefinition(
                List.of(start(), new GraphNode("n_start2", GraphNodeType.START,
                        new GraphNodeData(null, null, null, null, null)), end("n_end")),
                List.of(edge("e1", "n_start", "n_end"), edge("e2", "n_start2", "n_end")));
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("START"));
    }

    @Test
    void missingEndIsReported() {
        var g = new GraphDefinition(
                List.of(start(), form("n1", "erfassen")),
                List.of(edge("e1", "n_start", "n1")));
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("END"));
    }

    @Test
    void unreachableNodeIsReported() {
        var g = new GraphDefinition(
                List.of(start(), form("n1", "erfassen"), form("n2", "verwaist"), end("n_end")),
                List.of(edge("e1", "n_start", "n1"), edge("e2", "n1", "n_end"),
                        edge("e3", "n2", "n_end")));
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("verwaist") || e.contains("n2"));
    }

    @Test
    void deadEndNodeIsReported() {
        var g = new GraphDefinition(
                List.of(start(), form("n1", "erfassen"), end("n_end")),
                List.of(edge("e1", "n_start", "n1")));
        // n1 has no outgoing edge -> the flow can never reach an END through it
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("n1") || e.contains("erfassen"));
    }

    @Test
    void edgeToUnknownNodeIsReported() {
        var g = new GraphDefinition(
                List.of(start(), end("n_end")),
                List.of(edge("e1", "n_start", "ghost")));
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("ghost"));
    }

    @Test
    void duplicateKeysAreReported() {
        var g = new GraphDefinition(
                List.of(start(), form("n1", "doppelt"), form("n2", "doppelt"), end("n_end")),
                List.of(edge("e1", "n_start", "n1"), edge("e2", "n1", "n2"), edge("e3", "n2", "n_end")));
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("doppelt"));
    }

    @Test
    void invalidKeyPatternIsReported() {
        var g = new GraphDefinition(
                List.of(start(), form("n1", "kein-bindestrich"), end("n_end")),
                List.of(edge("e1", "n_start", "n1"), edge("e2", "n1", "n_end")));
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("kein-bindestrich"));
    }

    @Test
    void actionWithoutRefIsReported() {
        var g = new GraphDefinition(
                List.of(start(), action("n1", "provision", null), end("n_end")),
                List.of(edge("e1", "n_start", "n1"), edge("e2", "n1", "n_end")));
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("ref"));
    }

    @Test
    void xorBranchesNeedConditionsExceptOneDefault() {
        var ok = new GraphDefinition(
                List.of(start(), xor("nx", "entscheid"), end("n_e1"), end("n_e2")),
                List.of(edge("e1", "n_start", "nx"),
                        cond("e2", "nx", "n_e1", "entscheid_outcome == 'approve'"),
                        edge("e3", "nx", "n_e2")));   // one unconditioned = default
        assertThat(GraphValidator.validate(ok)).isEmpty();

        var twoDefaults = new GraphDefinition(
                List.of(start(), xor("nx", "entscheid"), end("n_e1"), end("n_e2")),
                List.of(edge("e1", "n_start", "nx"),
                        edge("e2", "nx", "n_e1"),
                        edge("e3", "nx", "n_e2")));   // two unconditioned branches
        assertThat(GraphValidator.validate(twoDefaults)).anyMatch(e -> e.contains("entscheid"));
    }

    /**
     * Prototyp-Anforderung „Kosten > CHF 5'000 ⇒ HAL-Stufe": numerische Vergleiche
     * mit Zahlen-Literal sind erlaubt; Ordnungsoperatoren mit String-Literal nicht
     * (lexikografische Falle).
     */
    @Test
    void numericComparisonConditionsAreAccepted() {
        var g = new GraphDefinition(
                List.of(start(), xor("nx", "kostenpruefung"), end("n_e1"), end("n_e2")),
                List.of(edge("e1", "n_start", "nx"),
                        cond("e2", "nx", "n_e1", "kosten > 5000"),
                        edge("e3", "nx", "n_e2")));
        assertThat(GraphValidator.validate(g)).isEmpty();

        var decimals = new GraphDefinition(
                List.of(start(), xor("nx", "kostenpruefung"), end("n_e1"), end("n_e2")),
                List.of(edge("e1", "n_start", "nx"),
                        cond("e2", "nx", "n_e1", "pensum >= 80.5"),
                        edge("e3", "nx", "n_e2")));
        assertThat(GraphValidator.validate(decimals)).isEmpty();
    }

    @Test
    void orderingOperatorWithStringLiteralIsRejected() {
        var g = new GraphDefinition(
                List.of(start(), xor("nx", "kostenpruefung"), end("n_e1"), end("n_e2")),
                List.of(edge("e1", "n_start", "nx"),
                        cond("e2", "nx", "n_e1", "kosten > 'fuenftausend'"),
                        edge("e3", "nx", "n_e2")));
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("condition"));
    }

    @Test
    void equalityWithNumericLiteralIsAccepted() {
        var g = new GraphDefinition(
                List.of(start(), xor("nx", "stufenwahl"), end("n_e1"), end("n_e2")),
                List.of(edge("e1", "n_start", "nx"),
                        cond("e2", "nx", "n_e1", "stufe == 2"),
                        edge("e3", "nx", "n_e2")));
        assertThat(GraphValidator.validate(g)).isEmpty();
    }

    /** Bedingungen sind eine eng begrenzte Sprache (var == 'wert'), KEIN freies JUEL — Injection-Kanal. */
    @Test
    void freeFormJuelConditionIsRejected() {
        var g = new GraphDefinition(
                List.of(start(), xor("nx", "entscheid"), end("n_e1"), end("n_e2")),
                List.of(edge("e1", "n_start", "nx"),
                        cond("e2", "nx", "n_e1", "evilBean.run()"),
                        edge("e3", "nx", "n_e2")));
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("condition"));
    }

    @Test
    void conditionOnNonXorEdgeIsReported() {
        var g = new GraphDefinition(
                List.of(start(), form("n1", "erfassen"), end("n_end")),
                List.of(cond("e1", "n_start", "n1", "x == 'y'"), edge("e2", "n1", "n_end")));
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("condition"));
    }

    @Test
    void andNodeMayNotSplitAndJoinAtOnce() {
        var g = new GraphDefinition(
                List.of(start(), form("n1", "a"), form("n2", "b"),
                        new GraphNode("nand", GraphNodeType.AND, new GraphNodeData("par", T, null, null, null)),
                        form("n3", "c"), form("n4", "d"), end("n_end")),
                List.of(edge("e1", "n_start", "n1"), edge("e2", "n_start", "n2"),
                        edge("e3", "n1", "nand"), edge("e4", "n2", "nand"),
                        edge("e5", "nand", "n3"), edge("e6", "nand", "n4"),
                        edge("e7", "n3", "n_end"), edge("e8", "n4", "n_end")));
        assertThat(GraphValidator.validate(g)).anyMatch(e -> e.contains("par"));
    }
}
