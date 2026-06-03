package io.github.manormachine2207.hrsuite.action;

import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only query service for the tenant's n8n action-ref allow-list.
 * Must be a {@code @Service} so that {@code TenantContextAspect} pushes the
 * {@code app.tenant_id} GUC on the DB connection before RLS-scoped reads
 * (ADR-008). The controller must not call {@link TenantN8nConfigRepository}
 * directly — the aspect only advises {@code @Service} methods, so a direct
 * repo call from a {@code @RestController} would skip the GUC and see no rows.
 */
@Service
@Transactional(readOnly = true)
public class ActionRefsService {

    private final TenantN8nConfigRepository configRepo;

    public ActionRefsService(TenantN8nConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    /**
     * Returns the {@code allowed_refs} for the current tenant, or an empty list
     * when no config row exists. RLS scopes the read to the current tenant's GUC.
     */
    public List<String> getAllowedRefs() {
        return configRepo.findById(TenantContext.require())
                .map(TenantN8nConfig::getAllowedRefs)
                .orElseGet(List::of);
    }
}
