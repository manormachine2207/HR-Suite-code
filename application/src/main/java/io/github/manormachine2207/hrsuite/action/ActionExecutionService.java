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

    public ActionExecution run(String processInstanceId, String stepKey, String ref, Map<String, Object> input) {
        UUID tenantId = TenantContext.require();

        ActionExecution exec = repo.findByProcessInstanceIdAndStepKey(processInstanceId, stepKey)
                .orElseGet(() -> repo.save(new ActionExecution(tenantId, processInstanceId, stepKey, ref)));
        if (exec.getStatus() == ActionStatus.SUCCEEDED) {
            return exec; // idempotent: a retried BPMN step must not re-fire the side effect
        }
        exec.markRunning();

        ActionRequest request = new ActionRequest(tenantId, processInstanceId, stepKey, ref, input);
        ActionResult last = null;
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
