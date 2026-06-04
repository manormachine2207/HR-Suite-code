package io.github.manormachine2207.hrsuite.antragstyp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.manormachine2207.hrsuite.antragstyp.flow.FlowDefinition;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateVersionRequest(
        @NotNull FormDefinition formDefinition,
        String workflowBpmn,
        Map<String, Object> sfActionBindings,
        FlowDefinition flowDefinition,    // optional; compiled to BPMN at publish()
        JsonNode graphDefinition          // optional; opaque free-form graph (ADR-012 SP2), not compiled
) {
}
