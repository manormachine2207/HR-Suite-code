package io.github.manormachine2207.hrsuite.antragstyp.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SP1-Voll-Validierung des Freiform-Graphen (ADR-012): genau ein START, mindestens
 * ein END, alles erreichbar, keine Sackgassen, Key-Disziplin (Keys werden BPMN-IDs
 * und JUEL-Variablen), XOR-Bedingungen in eng begrenzter Syntax, saubere AND-Knoten.
 * Pure Funktion — kein Spring.
 */
public final class GraphValidator {

    /** Mirrors the BpmnCompiler key constraint (BPMN element id + JUEL variable name). */
    public static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    /**
     * Eng begrenzte Bedingungssprache fuer XOR-Ausgaenge: {@code var == 'wert'} /
     * {@code var != 'wert'} fuer Strings, plus numerische Vergleiche
     * {@code var > 5000} (==, !=, >, >=, <, <= mit Zahlen-Literal — Prototyp-
     * Anforderung „Kosten > CHF 5'000 ⇒ HAL-Stufe", 2026-06-12). Bewusst KEIN
     * freies JUEL: die Bedingung landet in einer Flowable-Expression, freier Text
     * waere ein Injection-Kanal. Gruppen: 1=Variable, 2=Operator, 3=String-Literal
     * (oder null), 4=Zahlen-Literal (oder null). Ordnungsoperatoren verlangen ein
     * Zahlen-Literal (sonst lexikografische Falle) — erzwungen in {@link #validate}.
     */
    public static final Pattern CONDITION_PATTERN = Pattern.compile(
            "^\\s*([A-Za-z][A-Za-z0-9_]*)\\s*(==|!=|>=|<=|>|<)\\s*"
            + "(?:'([A-Za-z0-9_ .\\-äöüÄÖÜéèêàçÉÈÀ]*)'|(\\d+(?:\\.\\d+)?))\\s*$");

    private static final Set<String> ORDERING_OPERATORS = Set.of(">", ">=", "<", "<=");

    private GraphValidator() {
    }

    /** Returns human-readable issues; empty = compilable. */
    public static List<String> validate(GraphDefinition graph) {
        List<String> errors = new ArrayList<>();
        List<GraphNode> nodes = graph.nodes();
        Map<String, GraphNode> byId = new HashMap<>();
        for (GraphNode n : nodes) {
            if (byId.put(n.id(), n) != null) {
                errors.add("duplicate node id '" + n.id() + "'");
            }
        }

        // exactly one START, at least one END
        List<GraphNode> starts = nodes.stream().filter(n -> n.type() == GraphNodeType.START).toList();
        if (starts.size() != 1) {
            errors.add("graph must have exactly one START node (found " + starts.size() + ")");
        }
        if (nodes.stream().noneMatch(n -> n.type() == GraphNodeType.END)) {
            errors.add("graph must have at least one END node");
        }

        // edge endpoint integrity
        for (GraphEdge e : graph.edges()) {
            if (!byId.containsKey(e.source())) {
                errors.add("edge '" + e.id() + "' references unknown source node '" + e.source() + "'");
            }
            if (!byId.containsKey(e.target())) {
                errors.add("edge '" + e.id() + "' references unknown target node '" + e.target() + "'");
            }
        }

        // keys: required, well-formed, unique (START/END carry none)
        Set<String> seenKeys = new HashSet<>();
        for (GraphNode n : nodes) {
            if (n.type() == GraphNodeType.START || n.type() == GraphNodeType.END) {
                continue;
            }
            String key = n.data().key();
            if (key == null || !KEY_PATTERN.matcher(key).matches()) {
                errors.add("node '" + n.id() + "': key '" + key
                        + "' must match [A-Za-z][A-Za-z0-9_]* (becomes BPMN id / JUEL variable)");
            } else if (!seenKeys.add(key)) {
                errors.add("duplicate node key '" + key + "'");
            }
            if (n.type() == GraphNodeType.ACTION
                    && (n.data().ref() == null || n.data().ref().isBlank())) {
                errors.add("ACTION node '" + nodeName(n) + "': ref is required");
            }
        }

        // degrees
        Map<String, Integer> in = new HashMap<>();
        Map<String, Integer> out = new HashMap<>();
        for (GraphEdge e : graph.edges()) {
            out.merge(e.source(), 1, Integer::sum);
            in.merge(e.target(), 1, Integer::sum);
        }
        for (GraphNode n : nodes) {
            int outDeg = out.getOrDefault(n.id(), 0);
            int inDeg = in.getOrDefault(n.id(), 0);
            switch (n.type()) {
                case START -> {
                    if (inDeg > 0) {
                        errors.add("START node must not have incoming edges");
                    }
                    if (outDeg == 0) {
                        errors.add("START node has no outgoing edge");
                    }
                }
                case END -> {
                    if (outDeg > 0) {
                        errors.add("END node '" + n.id() + "' must not have outgoing edges");
                    }
                }
                case AND -> {
                    if (inDeg > 1 && outDeg > 1) {
                        errors.add("AND node '" + nodeName(n)
                                + "' may either split (1 in, n out) or join (n in, 1 out), not both");
                    }
                    if (outDeg == 0) {
                        errors.add("node '" + nodeName(n) + "' has no outgoing edge (dead end)");
                    }
                }
                default -> {
                    if (outDeg == 0) {
                        errors.add("node '" + nodeName(n) + "' has no outgoing edge (dead end)");
                    }
                }
            }
        }

        // XOR branch conditions: every outgoing edge of a >1-fan-out XOR carries a valid
        // condition, except at most ONE unconditioned edge (= gateway default).
        for (GraphNode n : nodes) {
            if (n.type() != GraphNodeType.XOR) {
                continue;
            }
            List<GraphEdge> outgoing = graph.edges().stream()
                    .filter(e -> e.source().equals(n.id())).toList();
            long unconditioned = outgoing.stream()
                    .filter(e -> e.condition() == null || e.condition().isBlank()).count();
            if (outgoing.size() > 1 && unconditioned > 1) {
                errors.add("XOR node '" + nodeName(n) + "': more than one outgoing edge without a "
                        + "condition (at most one default branch is allowed)");
            }
        }
        for (GraphEdge e : graph.edges()) {
            if (e.condition() == null || e.condition().isBlank()) {
                continue;
            }
            GraphNode source = byId.get(e.source());
            if (source != null && source.type() != GraphNodeType.XOR) {
                errors.add("edge '" + e.id() + "': condition is only allowed on XOR outgoing edges");
            } else {
                var m = CONDITION_PATTERN.matcher(e.condition());
                if (!m.matches()) {
                    errors.add("edge '" + e.id() + "': condition '" + e.condition()
                            + "' must have the form: variable == 'value' or variable > number");
                } else if (m.group(3) != null && ORDERING_OPERATORS.contains(m.group(2))) {
                    errors.add("edge '" + e.id() + "': condition '" + e.condition()
                            + "' — ordering comparisons (>, >=, <, <=) require a numeric literal");
                }
            }
        }

        // reachability from START (only when there is exactly one)
        if (starts.size() == 1) {
            Set<String> reachable = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(starts.get(0).id());
            reachable.add(starts.get(0).id());
            while (!queue.isEmpty()) {
                String current = queue.poll();
                for (GraphEdge e : graph.edges()) {
                    if (e.source().equals(current) && reachable.add(e.target())) {
                        queue.add(e.target());
                    }
                }
            }
            for (GraphNode n : nodes) {
                if (!reachable.contains(n.id())) {
                    errors.add("node '" + nodeName(n) + "' is not reachable from START");
                }
            }
        }
        return errors;
    }

    private static String nodeName(GraphNode n) {
        return n.data().key() != null ? n.data().key() : n.id();
    }
}
