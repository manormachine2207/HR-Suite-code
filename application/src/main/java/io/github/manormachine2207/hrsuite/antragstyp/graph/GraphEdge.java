package io.github.manormachine2207.hrsuite.antragstyp.graph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Eine Kante des Freiform-Graphen. {@code condition} ist nur auf XOR-Ausgaengen
 * erlaubt und folgt der eng begrenzten Syntax {@code var == 'wert'} /
 * {@code var != 'wert'} (siehe {@link GraphValidator#CONDITION_PATTERN}) — bewusst
 * KEIN freies JUEL (Injection-Kanal, Review 2026-06-12).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphEdge(
        String id,
        String source,
        String target,
        String sourceHandle,
        String label,
        String condition) {
}
