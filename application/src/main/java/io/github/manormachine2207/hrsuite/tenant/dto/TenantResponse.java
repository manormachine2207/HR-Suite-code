package io.github.manormachine2207.hrsuite.tenant.dto;

import io.github.manormachine2207.hrsuite.tenant.Tenant;
import io.github.manormachine2207.hrsuite.tenant.TenantStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String code,
        Map<String, String> displayName,
        String subdomain,
        TenantStatus status,
        String defaultLocale,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static TenantResponse from(Tenant t) {
        return new TenantResponse(
                t.getId(), t.getCode(), t.getDisplayName(), t.getSubdomain(),
                t.getStatus(), t.getDefaultLocale(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
