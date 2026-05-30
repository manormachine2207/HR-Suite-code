package io.github.manormachine2207.hrsuite.action;

import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Test-only harness for {@code N8nActionConnectorIT}. Exists solely so the
 * RLS-scoped writes/reads run under the production GUC machinery: this bean is a
 * {@code @Service @Transactional}, so {@code TenantContextAspect} advises its
 * public methods and pushes {@code app.tenant_id} onto the bound transactional
 * connection (ADR-008) exactly as it does for {@code AntragService}. The caller
 * sets {@link TenantContext} on the thread first (what {@code TenantContextFilter}
 * does on the HTTP path); from there the Flowable deploy/start and the
 * {@code action_execution} reads share one transaction with the GUC set, so the
 * synchronous {@code n8nActionDelegate} hits RLS-bound tables correctly.
 *
 * <p>Lives in the {@code action} test package, not the main source tree, so it
 * never ships and never participates in Spring Modulith verification (which scans
 * main classes only).
 */
@Service
@Transactional
public class ActionItHarness {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TenantN8nConfigRepository configRepo;
    private final ActionExecutionRepository executionRepo;

    public ActionItHarness(RepositoryService repositoryService,
                           RuntimeService runtimeService,
                           TenantN8nConfigRepository configRepo,
                           ActionExecutionRepository executionRepo) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.configRepo = configRepo;
        this.executionRepo = executionRepo;
    }

    /** Seeds the RLS-scoped {@code tenant_n8n_config} row for the current tenant. */
    public void seedConfig(UUID tenant, String baseUrl, String hmacSecret, List<String> allowedRefs) {
        configRepo.save(new TenantN8nConfig(tenant, baseUrl, hmacSecret, allowedRefs));
    }

    /** Deploys the action test BPMN for the given Flowable tenant. */
    public void deployProcess(UUID tenant) {
        repositoryService.createDeployment()
                .name("action-it")
                .tenantId(tenant.toString())
                .addClasspathResource("bpmn/action-test-process.bpmn20.xml")
                .deploy();
    }

    /**
     * Starts the action test process in the given tenant with the supplied
     * {@code actionInput}. The {@code n8nActionDelegate} runs synchronously inside
     * this transaction (GUC set by the aspect), so the RLS-bound
     * {@code action_execution} write happens under the right tenant. Returns the
     * process instance id. Any {@code BpmnError} the delegate raises on DEAD/FAILED
     * surfaces to the caller, who wraps it.
     */
    public String startProcess(UUID tenant, Map<String, Object> actionInput) {
        return runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey("actionTestProcess")
                .tenantId(tenant.toString())
                .variable("actionInput", actionInput)
                .start()
                .getId();
    }

    /** Reads back the single {@code action_execution} row for a process instance + step. */
    public Optional<ActionExecution> findExecution(String processInstanceId, String stepKey) {
        return executionRepo.findByProcessInstanceIdAndStepKey(processInstanceId, stepKey);
    }

    /**
     * Finds the single DEAD {@code action_execution} for a ref within the current
     * tenant's RLS scope. Used by the dead-letter test, where the BpmnError swallows
     * the process instance id so {@link #findExecution} can't be keyed by it.
     */
    public Optional<ActionExecution> findDeadExecution(String ref) {
        return executionRepo.findAll().stream()
                .filter(e -> ref.equals(e.getRef()))
                .filter(e -> e.getStatus() == ActionStatus.DEAD)
                .findFirst();
    }

    /** Counts {@code action_execution} rows visible under the current tenant's RLS scope. */
    public long countExecutionsVisible() {
        return executionRepo.count();
    }
}
