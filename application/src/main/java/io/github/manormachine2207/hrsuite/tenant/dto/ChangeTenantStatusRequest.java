package io.github.manormachine2207.hrsuite.tenant.dto;

import io.github.manormachine2207.hrsuite.tenant.TenantStatus;
import jakarta.validation.constraints.NotNull;

/** Body for PATCH /api/v1/tenant/{id}/status (ADR-019 Stufe 1). */
public record ChangeTenantStatusRequest(@NotNull TenantStatus status) {
}
