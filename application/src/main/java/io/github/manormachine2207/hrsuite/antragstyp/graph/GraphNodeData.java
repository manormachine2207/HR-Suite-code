package io.github.manormachine2207.hrsuite.antragstyp.graph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Typ-spezifische Knotendaten (Kontrakt mit dem SP2-Canvas: {@code NodeData}).
 * START/END tragen nichts; FORM/APPROVAL/ACTION/XOR/AND tragen {@code key}+{@code title};
 * APPROVAL zusaetzlich {@code assigneeRole}, ACTION {@code ref}+{@code inputMapping}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphNodeData(
        String key,
        Map<String, String> title,
        String assigneeRole,
        String ref,
        Map<String, String> inputMapping) {

    public GraphNodeData {
        title = title == null ? Map.of() : Map.copyOf(title);
        inputMapping = inputMapping == null ? Map.of() : Map.copyOf(inputMapping);
    }
}
