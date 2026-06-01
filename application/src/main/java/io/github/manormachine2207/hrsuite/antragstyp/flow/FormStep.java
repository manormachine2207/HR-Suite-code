package io.github.manormachine2207.hrsuite.antragstyp.flow;

import java.util.Map;

/** A form-fill step: the applicant sees and fills in the associated {@code formDefinition}. */
public record FormStep(String key, Map<String, String> title) implements FlowStep {
}
