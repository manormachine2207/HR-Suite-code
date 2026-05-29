package io.github.manormachine2207.hrsuite.shared.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Thread-local holder of the current tenant id. Populated per request by
 * {@link TenantContextFilter} from the JWT {@code tenant_id} claim and consumed
 * by {@link TenantContextAspect}, which pushes it onto the transactional DB
 * connection as the {@code app.tenant_id} session GUC for PostgreSQL RLS
 * (ADR-008, ADR-009). Lives in the OPEN {@code shared} module so both the
 * {@code tenant} and {@code antragstyp} modules can use it without a cycle.
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

    /**
     * Returns the current tenant id, or throws {@link MissingTenantContextException}
     * (mapped to HTTP 403) when none is set. Tenant-scoped service code must use this
     * instead of unwrapping {@link #get()} with a raw {@link IllegalStateException},
     * which would surface as a 500 on the auth path (ADR-008).
     */
    public static UUID require() {
        return get().orElseThrow(
                () -> new MissingTenantContextException("no tenant in context"));
    }

    public static void clear() {
        CURRENT.remove();
    }
}
