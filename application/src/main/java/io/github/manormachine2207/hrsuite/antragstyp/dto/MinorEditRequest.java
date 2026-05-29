package io.github.manormachine2207.hrsuite.antragstyp.dto;

import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import jakarta.validation.constraints.NotNull;

public record MinorEditRequest(
        @NotNull FormDefinition formDefinition
) {
}
