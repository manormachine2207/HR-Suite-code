package io.github.manormachine2207.hrsuite.action;

import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Runs a workflow action through the {@link ActionConnector} with idempotency
 * (one {@link ActionExecution} per process-instance+step) and bounded immediate
 * retries; a transient failure exhausting retries lands in DEAD (dead-letter).
 * Audit is the persisted row + a structured log event (SDR-002 minimum until the
 * audit module lands).
 */
@Service
@Transactional
public class ActionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutionService.class);

    private final ActionConnector connector;
    private final ActionExecutionRepository repo;
    private final DeadLetterWriter deadLetterWriter;
    private final int maxAttempts;

    public ActionExecutionService(ActionConnector connector,
                                  ActionExecutionRepository repo,
                                  DeadLetterWriter deadLetterWriter,
                                  @Value("${hrsuite.action.max-attempts:3}") int maxAttempts) {
        this.connector = connector;
        this.repo = repo;
        this.deadLetterWriter = deadLetterWriter;
        this.maxAttempts = maxAttempts;
    }

    public ActionExecution run(String processInstanceId, String businessKey, String stepKey,
                               String ref, Map<String, Object> input) {
        UUID tenantId = TenantContext.require();

        // Lookup anchored on the business key (= antrag id) when present: a resubmit after a
        // rollback starts a NEW process instance, so processInstanceId alone cannot dedupe
        // side effects across submits (Review 2026-06-12). RLS scopes the query to the tenant.
        ActionExecution exec = (businessKey != null
                ? repo.findByBusinessKeyAndStepKey(businessKey, stepKey)
                : repo.findByProcessInstanceIdAndStepKey(processInstanceId, stepKey))
                .orElseGet(() -> repo.save(new ActionExecution(tenantId, processInstanceId, businessKey, stepKey, ref)));
        // Idempotency guard: SUCCEEDED short-circuits (side effect already out), and
        // FAILED/DEAD are terminal — a dead-lettered action must not revive itself on a
        // BPMN re-entry; the delegate raises BpmnError from the returned status. Only
        // PENDING/RUNNING (fresh row or crash recovery) proceed to attempt.
        if (exec.getStatus() == ActionStatus.SUCCEEDED
                || exec.getStatus() == ActionStatus.FAILED
                || exec.getStatus() == ActionStatus.DEAD) {
            return exec;
        }
        exec.markRunning();

        ActionRequest request = new ActionRequest(tenantId, processInstanceId, businessKey, stepKey, ref, input);
        ActionResult last = null;
        // Retries are immediate (no backoff) on purpose for Cut A: introducing Thread.sleep
        // here would hold the Flowable command thread and its DB connection for the full
        // back-off interval.  Exponential backoff / async job execution is a deferred
        // follow-up once the async action queue (Cut B/C) is in place.
        while (exec.getAttempts() < maxAttempts) {
            last = connector.execute(request);
            exec.recordAttempt(last.error());
            if (last.success()) {
                exec.markSucceeded();
                log.info("action SUCCEEDED tenant={} pi={} step={} ref={} attempts={}",
                        tenantId, processInstanceId, stepKey, ref, exec.getAttempts());
                return repo.save(exec);
            }
            if (!last.retryable()) {
                exec.markFailed(last.error());
                log.warn("action FAILED (terminal) tenant={} pi={} step={} ref={} error={}",
                        tenantId, processInstanceId, stepKey, ref, last.error());
                // Commit the dead-letter independently: the delegate raises BpmnError on a
                // terminal outcome, which rolls back the process-start command (and thus the
                // caller's transaction). Persist out-of-band so the DLQ record survives.
                return deadLetterWriter.persistTerminal(exec);
            }
        }
        exec.markDead(last == null ? "no attempt" : last.error());
        log.error("action DEAD (dead-letter) tenant={} pi={} step={} ref={} attempts={} error={}",
                tenantId, processInstanceId, stepKey, ref, exec.getAttempts(), exec.getLastError());
        return deadLetterWriter.persistTerminal(exec);
    }
}
