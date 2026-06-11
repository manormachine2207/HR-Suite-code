package io.github.manormachine2207.hrsuite.antragstyp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.manormachine2207.hrsuite.antragstyp.flow.FlowDefinition;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateVersionRequest(
        @NotNull FormDefinition formDefinition,
        Map<String, Object> sfActionBindings,
        FlowDefinition flowDefinition,    // optional; compiled to BPMN at publish()
        JsonNode graphDefinition          // optional; opaque free-form graph (ADR-012 SP2), not compiled
) {
    // workflowBpmn was removed on purpose (Review 2026-06-12): BPMN is compiler output
    // only (ADR-010 "HR sieht kein BPMN"); accepting raw XML here was an injection channel.
    // Unknown JSON properties are ignored by the default Jackson config, so old clients
    // that still send the field are tolerated — the value just never reaches the engine.
}
