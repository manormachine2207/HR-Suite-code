package io.github.manormachine2207.hrsuite.antragstyp.dto;

import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateVersionRequest(
        @NotNull FormDefinition formDefinition,
        String workflowBpmn,
        Map<String, Object> sfActionBindings
) {
}
