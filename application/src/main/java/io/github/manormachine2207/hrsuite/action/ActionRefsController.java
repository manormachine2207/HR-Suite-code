package io.github.manormachine2207.hrsuite.action;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only listing of the n8n action references allow-listed for the current tenant
 * ({@code tenant_n8n_config.allowed_refs}). Used by the Cut C flow editor to populate the
 * ACTION step's ref dropdown. Authoring-only: restricted to hr-designer.
 *
 * <p>Delegates to {@link ActionRefsService} — a {@code @Service} bean — so that
 * {@code TenantContextAspect} pushes the {@code app.tenant_id} GUC (ADR-008) before
 * the RLS-scoped repository read. A direct repo call from a {@code @RestController}
 * would bypass the aspect and return an empty set regardless of tenant.
 */
@RestController
@RequestMapping("/api/v1/action")
public class ActionRefsController {

    private final ActionRefsService refsService;

    public ActionRefsController(ActionRefsService refsService) {
        this.refsService = refsService;
    }

    @GetMapping("/refs")
    @PreAuthorize("hasRole('hr-designer')")
    public List<String> refs() {
        return refsService.getAllowedRefs();
    }
}
