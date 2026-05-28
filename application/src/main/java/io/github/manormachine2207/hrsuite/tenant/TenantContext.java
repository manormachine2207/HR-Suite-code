package io.github.manormachine2207.hrsuite.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Thread-local holder of the current tenant id. Foundation for PostgreSQL
 * RLS enforcement (ADR-008): a later cut adds a request filter that populates
 * this from the authenticated principal and a TenantContextAspect that issues
 * {@code SET app.tenant_id} on the transactional connection. In this cut the
 * holder exists but is never populated (the only table, tenant, is outside RLS).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static Optional<UUID> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
