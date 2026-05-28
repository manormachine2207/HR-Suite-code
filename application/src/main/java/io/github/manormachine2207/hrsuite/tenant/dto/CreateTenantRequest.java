package io.github.manormachine2207.hrsuite.tenant.dto;

import io.github.manormachine2207.hrsuite.tenant.TenantStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateTenantRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
        @NotEmpty Map<String, String> displayName,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z0-9-]+$") String subdomain,
        @Pattern(regexp = "^(de|fr|it|en)$") String defaultLocale,
        TenantStatus status
) {
}
