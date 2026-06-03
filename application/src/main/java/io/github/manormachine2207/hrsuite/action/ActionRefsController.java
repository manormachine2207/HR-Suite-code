package io.github.manormachine2207.hrsuite.action;

import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only listing of the n8n action references allow-listed for the current tenant
 * ({@code tenant_n8n_config.allowed_refs}). Used by the Cut C flow editor to populate the
 * ACTION step's ref dropdown. Authoring-only: restricted to hr-designer. RLS (ADR-008)
 * already scopes the row by {@code app.tenant_id}; we additionally look up by the
 * TenantContext id so an empty/absent config yields an empty list (never another tenant's).
 */
@RestController
@RequestMapping("/api/v1/action")
public class ActionRefsController {

    private final TenantN8nConfigRepository configRepo;

    public ActionRefsController(TenantN8nConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    @GetMapping("/refs")
    @PreAuthorize("hasRole('hr-designer')")
    public List<String> refs() {
        return configRepo.findById(TenantContext.require())
                .map(TenantN8nConfig::getAllowedRefs)
                .orElseGet(List::of);
    }
}
