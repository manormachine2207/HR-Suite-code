package io.github.manormachine2207.hrsuite.review;

import io.github.manormachine2207.hrsuite.antrag.Antrag;
import io.github.manormachine2207.hrsuite.antrag.AntragService;
import io.github.manormachine2207.hrsuite.antrag.AntragStatus;
import io.github.manormachine2207.hrsuite.antragstyp.AntragsTypService;
import io.github.manormachine2207.hrsuite.review.dto.TaskResponse;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import io.github.manormachine2207.hrsuite.workflow.WorkflowEngine;
import io.github.manormachine2207.hrsuite.workflow.WorkflowTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Anwendungsdienst des Review-Pfads (ADR-013): Task-Inbox (tenant- und rollen-
 * gescopt), Complete mit Outcome-Validierung und synchronem Status-Sync auf den
 * Antrag — alles im Request-Thread/derselben Transaktion, damit die Tenant-GUC
 * (ADR-008) garantiert gesetzt ist.
 */
@Service
@Transactional
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** Outcome wird Prozessvariablen-Wert; gleiche Disziplin wie Compiler-Keys (Injection). */
    private static final Pattern OUTCOME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    private final WorkflowEngine workflowEngine;
    private final AntragService antragService;
    private final AntragsTypService antragsTypService;
    private final io.github.manormachine2207.hrsuite.notification.NotificationService notificationService;

    public ReviewService(WorkflowEngine workflowEngine, AntragService antragService,
                         AntragsTypService antragsTypService,
                         io.github.manormachine2207.hrsuite.notification.NotificationService notificationService) {
        this.workflowEngine = workflowEngine;
        this.antragService = antragService;
        this.antragsTypService = antragsTypService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listOpenTasks(Collection<String> callerGroups) {
        UUID tenantId = TenantContext.require();
        return workflowEngine.openTasks(tenantId, callerGroups).stream()
                .map(t -> toResponse(t, false))
                .toList();
    }

    /** Gruppenlose Tasks bearbeitet die Basis-Review-Rolle; Gruppen-Tasks nur Mitglieder (ADR-016). */
    private static final java.util.Set<String> BASE_REVIEW_GROUPS =
            java.util.Set.of("hr-reviewer", "tenant-admin");

    @Transactional(readOnly = true)
    public TaskResponse getTask(String taskId, Collection<String> callerGroups) {
        UUID tenantId = TenantContext.require();
        WorkflowTask task = workflowEngine.findOpenTask(tenantId, taskId)
                .orElseThrow(() -> new ReviewExceptions.NotFound("task not found: " + taskId));
        assertCallerMayWork(task, callerGroups);
        return toResponse(task, true);
    }

    /**
     * ADR-016: Sichtbarkeit in der Inbox UND Bearbeitung am Task-Endpoint folgen
     * derselben Regel — ein Nicht-Mitglied bekommt 404 (kein Existenz-Leak,
     * gleiches Muster wie die Applicant-Ownership-Grenze im Antrag-Modul).
     */
    private static void assertCallerMayWork(WorkflowTask task, Collection<String> callerGroups) {
        boolean allowed = task.candidateGroups().isEmpty()
                ? callerGroups.stream().anyMatch(BASE_REVIEW_GROUPS::contains)
                : callerGroups.stream().anyMatch(task.candidateGroups()::contains);
        if (!allowed) {
            throw new ReviewExceptions.NotFound("task not found: " + task.id());
        }
    }

    /**
     * Completes the task and syncs the antrag status in the same transaction:
     * process still running → IN_REVIEW; ended → APPROVED/REJECTED by outcome,
     * COMPLETED without one (ADR-013).
     */
    public TaskResponse complete(String taskId, String outcome, String comment,
                                 Collection<String> callerGroups) {
        UUID tenantId = TenantContext.require();
        WorkflowTask task = workflowEngine.findOpenTask(tenantId, taskId)
                .orElseThrow(() -> new ReviewExceptions.NotFound("task not found: " + taskId));
        assertCallerMayWork(task, callerGroups);
        Antrag antrag = antragOf(task);

        String normalized = (outcome == null || outcome.isBlank()) ? null : outcome.trim();
        if (normalized != null) {
            if (!OUTCOME_PATTERN.matcher(normalized).matches()) {
                throw new ReviewExceptions.Invalid(
                        "outcome must match [A-Za-z][A-Za-z0-9_]* : " + normalized);
            }
            if (antrag != null && antrag.getAntragstypVersionId() != null) {
                List<String> declared = antragsTypService.findDeclaredOutcomes(
                        antrag.getAntragstypVersionId(), task.taskDefinitionKey());
                if (!declared.isEmpty() && !declared.contains(normalized)) {
                    throw new ReviewExceptions.Invalid("outcome '" + normalized
                            + "' is not declared for step '" + task.taskDefinitionKey()
                            + "' (allowed: " + String.join(", ", declared) + ")");
                }
            }
        }

        Map<String, Object> variables = normalized == null
                ? Map.of()
                : Map.of(task.taskDefinitionKey() + "_outcome", normalized);
        workflowEngine.completeTask(task.id(), variables);

        boolean running = workflowEngine.isInstanceRunning(tenantId, task.processInstanceId());
        if (antrag != null) {
            if (running) {
                antrag.beginReview();
            } else {
                AntragStatus terminal = terminalStatus(normalized);
                antrag.finishReview(terminal);
                // ADR-017 Stufe 2: notify the applicant their request was decided, in the
                // same transaction (rolls back with the decision if anything fails).
                notificationService.notifyAntragDecided(
                        antrag.getAntragstellerSubject(), antrag.getId(), terminal.name(),
                        antrag.getAntragstellerEmail(), antrag.getAntragstellerLocale());
            }
        }
        // SDR-002-Minimum bis zum Audit-Modul: strukturiertes Event (IDs, kein PII-Payload).
        log.info("antrag.reviewed tenant={} task={} step={} antrag={} outcome={} running={} comment={}",
                tenantId, task.id(), task.taskDefinitionKey(),
                antrag == null ? null : antrag.getId(), normalized, running,
                comment == null ? "" : "yes");

        return new TaskResponse(task.id(), task.name(), task.taskDefinitionKey(), task.createdAt(),
                antrag == null ? null : antrag.getId(),
                antrag == null ? null : antrag.getStatus().name(),
                antrag == null ? null : antragsTypService.getDefinition(antrag.getAntragstypId()).getTitle(),
                antrag == null ? null : antrag.getAntragstellerSubject(),
                null, null);
    }

    private static AntragStatus terminalStatus(String outcome) {
        if ("approve".equals(outcome)) {
            return AntragStatus.APPROVED;
        }
        if ("reject".equals(outcome)) {
            return AntragStatus.REJECTED;
        }
        return AntragStatus.COMPLETED;
    }

    private TaskResponse toResponse(WorkflowTask task, boolean includePayload) {
        Antrag antrag = antragOf(task);
        List<String> allowedOutcomes = (antrag == null || antrag.getAntragstypVersionId() == null)
                ? List.of()
                : antragsTypService.findDeclaredOutcomes(antrag.getAntragstypVersionId(), task.taskDefinitionKey());
        return new TaskResponse(
                task.id(), task.name(), task.taskDefinitionKey(), task.createdAt(),
                antrag == null ? null : antrag.getId(),
                antrag == null ? null : antrag.getStatus().name(),
                antrag == null ? null : antragsTypService.getDefinition(antrag.getAntragstypId()).getTitle(),
                antrag == null ? null : antrag.getAntragstellerSubject(),
                allowedOutcomes,
                includePayload && antrag != null ? antrag.getPayload() : null);
    }

    /**
     * Resolves the antrag behind the task's business key. Null-tolerant: tasks of
     * non-antrag processes (or pre-businessKey smoke instances) stay listable, just
     * without antrag context. RLS scopes the lookup to the tenant.
     */
    private Antrag antragOf(WorkflowTask task) {
        if (task.businessKey() == null) {
            return null;
        }
        try {
            return antragService.getForReview(UUID.fromString(task.businessKey()));
        } catch (IllegalArgumentException | io.github.manormachine2207.hrsuite.antrag.AntragExceptions.NotFound e) {
            return null;
        }
    }
}
