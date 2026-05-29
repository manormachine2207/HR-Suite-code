package io.github.manormachine2207.hrsuite.antragstyp.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateAntragsTypRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[a-z0-9_-]+$") String key,
        @NotEmpty Map<String, String> title,
        Map<String, String> description
) {
}
