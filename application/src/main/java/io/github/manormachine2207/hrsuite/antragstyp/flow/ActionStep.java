package io.github.manormachine2207.hrsuite.antragstyp.flow;

import java.util.Map;

/**
 * An automated action step: calls an n8n workflow via the {@code n8nActionDelegate}
 * Flowable service task. {@code ref} is the n8n webhook ref. {@code inputMapping} is
 * a static key→value map compiled into the BPMN as {@code inputMappingJson} field.
 */
public record ActionStep(
        String key,
        Map<String, String> title,
        String ref,
        Map<String, String> inputMapping) implements FlowStep {

    public ActionStep {
        inputMapping = inputMapping == null ? Map.of() : Map.copyOf(inputMapping);
    }
}
