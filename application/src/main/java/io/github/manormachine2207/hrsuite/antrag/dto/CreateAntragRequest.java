package io.github.manormachine2207.hrsuite.antrag.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/** Create a DRAFT request for the given antragstyp with optional initial form answers. */
public record CreateAntragRequest(
        @NotNull UUID antragstypId,
        Map<String, Object> payload
) {
}
