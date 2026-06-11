package io.github.manormachine2207.hrsuite.antragstyp.graph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Typisiertes Abbild der opak gespeicherten {@code graph_definition} (SP2) fuer
 * Validierung + Kompilierung (SP1, ADR-012). Der Editor speichert weiterhin opak;
 * geparst wird erst beim Publish.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphDefinition(
        List<GraphNode> nodes,
        List<GraphEdge> edges) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public GraphDefinition {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    /**
     * Parses the opaque stored JSON into the typed model.
     *
     * @throws IllegalArgumentException when the JSON does not match the SP2 contract
     *                                  (unknown node type, wrong shapes, ...)
     */
    public static GraphDefinition from(JsonNode json) {
        try {
            return MAPPER.treeToValue(json, GraphDefinition.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("graph definition does not match the editor contract: "
                    + e.getMessage(), e);
        }
    }
}
