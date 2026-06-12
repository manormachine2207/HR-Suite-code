package io.github.manormachine2207.hrsuite.antragstyp.graph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Ein Knoten des Freiform-Graphen. {@code position} aus dem Canvas wird beim
 * Kompilieren bewusst ignoriert (Layout ist Editor-Sache) und via
 * {@code ignoreUnknown} uebersprungen.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphNode(
        String id,
        GraphNodeType type,
        GraphNodeData data) {

    public GraphNode {
        data = data == null ? new GraphNodeData(null, null, null, null, null) : data;
    }
}
