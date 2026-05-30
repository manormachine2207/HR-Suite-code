package io.github.manormachine2207.hrsuite.workflow;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** Flowable-backed {@link WorkflowEngine} (ADR-004). */
@Component
public class FlowableWorkflowEngine implements WorkflowEngine {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;

    public FlowableWorkflowEngine(RepositoryService repositoryService, RuntimeService runtimeService) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
    }

    @Override
    public DeployedProcess deploy(UUID tenantId, String processKey, String name, String bpmnXml) {
        String xml = DefaultProcessBpmn.isDeployable(bpmnXml)
                ? bpmnXml
                : DefaultProcessBpmn.minimal(processKey, name);
        Deployment deployment = repositoryService.createDeployment()
                .name(name)
                .tenantId(tenantId.toString())
                .addString(processKey + ".bpmn20.xml", xml)
                .deploy();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        return new DeployedProcess(deployment.getId(), definition.getKey(), definition.getVersion());
    }

    @Override
    public String startInstance(UUID tenantId, String processDefinitionKey, String businessKey,
                                Map<String, Object> variables) {
        ProcessInstance instance = runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey(processDefinitionKey)
                .tenantId(tenantId.toString())
                .businessKey(businessKey)
                .variables(variables == null ? Map.of() : variables)
                .start();
        return instance.getId();
    }
}
