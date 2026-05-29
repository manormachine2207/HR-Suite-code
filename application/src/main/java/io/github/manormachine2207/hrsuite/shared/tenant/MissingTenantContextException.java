package io.github.manormachine2207.hrsuite.shared.tenant;

/**
 * Raised when a tenant-scoped operation runs with no tenant in the
 * {@link TenantContext} — e.g. an authenticated principal whose token carries a
 * tenant-scoped role (hr-designer, tenant-admin, …) but a missing, blank, or
 * malformed {@code tenant_id} claim, which {@link TenantContextFilter} maps to an
 * empty context. Mapped to HTTP 403 by the {@code ApiExceptionHandler}: the
 * caller is authenticated but cannot act in a tenant scope. This is a client /
 * token problem, not a server fault, so it must surface as a 4xx rather than the
 * 500 a raw {@link IllegalStateException} would produce (ADR-008).
 */
public class MissingTenantContextException extends RuntimeException {

    public MissingTenantContextException(String message) {
        super(message);
    }
}
