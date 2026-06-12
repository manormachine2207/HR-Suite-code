package io.github.manormachine2207.hrsuite.review;

import io.github.manormachine2207.hrsuite.review.dto.CompleteTaskRequest;
import io.github.manormachine2207.hrsuite.review.dto.TaskResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Review-API (ADR-013). Rollen: hr-reviewer / tenant-admin (04-Authorization-Model);
 * Tenant-Scope via TenantContext + Flowable-tenantId + RLS auf dem Antrag-Join.
 */
@RestController
@RequestMapping("/api/v1/task")
public class ReviewController {

    /** ADR-016: Genehmiger-Gruppen duerfen die Inbox sehen — die Engine filtert auf Mitgliedschaft. */
    private static final String REVIEW = "hasAnyRole('hr-reviewer','tenant-admin',"
            + "'approver-vg','approver-hr-bp','approver-hal')";

    /** Candidate-Group-Filter: Basis-Review-Rollen + fachliche Genehmiger-Gruppen (ADR-016). */
    private static final Set<String> REVIEW_GROUPS = Set.of(
            "hr-reviewer", "tenant-admin", "approver-vg", "approver-hr-bp", "approver-hal");

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(REVIEW)
    public List<TaskResponse> list(Authentication auth) {
        return service.listOpenTasks(callerGroups(auth));
    }

    @GetMapping("/{id}")
    @PreAuthorize(REVIEW)
    public TaskResponse get(@PathVariable("id") String id, Authentication auth) {
        return service.getTask(id, callerGroups(auth));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(REVIEW)
    public TaskResponse complete(@PathVariable("id") String id,
                                 @RequestBody(required = false) CompleteTaskRequest req,
                                 Authentication auth) {
        return service.complete(id,
                req == null ? null : req.outcome(),
                req == null ? null : req.comment(),
                callerGroups(auth));
    }

    private static Set<String> callerGroups(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .filter(REVIEW_GROUPS::contains)
                .collect(Collectors.toSet());
    }
}
