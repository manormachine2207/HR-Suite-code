package io.github.manormachine2207.hrsuite.action;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a terminal {@link ActionExecution} (FAILED / DEAD) in its OWN transaction
 * so the dead-letter record survives the rollback that {@link N8nActionDelegate}'s
 * {@code BpmnError} triggers. Without this, the delegate marks DEAD, returns, then
 * throws {@code BpmnError}; Flowable rolls back the process-start command — and with
 * it the DLQ row — leaving no audit trail, contrary to the Cut-A spec
 * ("Fehler → Retry → DLQ … Ergebnis landet in action_execution").
 *
 * <p>Being a {@code @Service}, this bean's public method is advised by
 * {@code TenantContextAspect}, which re-applies the {@code app.tenant_id} GUC onto the
 * fresh {@code REQUIRES_NEW} connection so the RLS-scoped write binds to the right
 * tenant (ADR-008). The successful path is intentionally NOT routed here: a success
 * must commit together with the process instance, in the caller's transaction.
 */
@Service
public class DeadLetterWriter {

    private final ActionExecutionRepository repo;

    public DeadLetterWriter(ActionExecutionRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ActionExecution persistTerminal(ActionExecution exec) {
        return repo.save(exec);
    }
}
