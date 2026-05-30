package io.github.manormachine2207.hrsuite.workflow;

import java.util.Map;
import java.util.UUID;

/**
 * Abstraction over the embedded BPMN engine (ADR-004), so antragstyp publish (deploy
 * the major's process) and antrag submit (start a process instance) stay decoupled
 * from Flowable and the engine remains swappable. All operations are scoped to the
 * HR-Suite tenant via Flowable's native tenant id.
 */
public interface WorkflowEngine {

    /**
     * Deploys an antragstyp major's BPMN for the tenant and returns the engine handles
     * to persist on the version. A non-deployable / placeholder {@code bpmnXml} is
     * replaced by a minimal default process ({@link DefaultProcessBpmn}).
     */
    DeployedProcess deploy(UUID tenantId, String processKey, String name, String bpmnXml);

    /**
     * Starts a process instance of the (latest) process definition for the given key in
     * the tenant and returns its instance id. {@code businessKey} ties it back to the
     * Antrag.
     */
    String startInstance(UUID tenantId, String processDefinitionKey, String businessKey,
                         Map<String, Object> variables);
}
