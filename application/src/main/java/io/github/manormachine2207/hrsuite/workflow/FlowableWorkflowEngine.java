package io.github.manormachine2207.hrsuite.workflow;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Flowable-backed {@link WorkflowEngine} (ADR-004). */
@Component
public class FlowableWorkflowEngine implements WorkflowEngine {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    public FlowableWorkflowEngine(RepositoryService repositoryService, RuntimeService runtimeService,
                                  TaskService taskService, HistoryService historyService) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
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

    // ---- review path (ADR-013) --------------------------------------------

    @Override
    public List<WorkflowTask> openTasks(UUID tenantId, Collection<String> callerGroups) {
        // Flowable 7.1 has no "without candidate groups" predicate, so a clean
        // "group-less OR in caller's groups" query is not expressible. Today every
        // compiler-emitted candidate group IS a review role (hr-reviewer/tenant-admin)
        // and the endpoint is role-guarded — the tenant's unassigned active set IS the
        // caller's inbox. Revisit with an identity-link post-filter once candidate
        // groups outside the review roles exist (callerGroups stays in the signature
        // for exactly that refinement).
        List<Task> tasks = taskService.createTaskQuery()
                .taskTenantId(tenantId.toString())
                .taskUnassigned()
                .active()
                .orderByTaskCreateTime().asc().list();
        Map<String, String> businessKeys = businessKeysFor(
                tasks.stream().map(Task::getProcessInstanceId).distinct().toList());
        return tasks.stream().map(t -> toWorkflowTask(t, businessKeys)).toList();
    }

    @Override
    public Optional<WorkflowTask> findOpenTask(UUID tenantId, String taskId) {
        Task task = taskService.createTaskQuery()
                .taskTenantId(tenantId.toString())
                .taskId(taskId)
                .active()
                .singleResult();
        if (task == null) {
            return Optional.empty();
        }
        return Optional.of(toWorkflowTask(task, businessKeysFor(List.of(task.getProcessInstanceId()))));
    }

    @Override
    public void completeTask(String taskId, Map<String, Object> variables) {
        if (variables != null && !variables.isEmpty()) {
            taskService.setVariables(taskId, variables);
        }
        taskService.complete(taskId);
    }

    @Override
    public boolean isInstanceRunning(UUID tenantId, String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .processInstanceTenantId(tenantId.toString())
                .count() > 0;
    }

    @Override
    public List<WorkflowStepInfo> instanceProgress(UUID tenantId, String processInstanceId) {
        return historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityTenantId(tenantId.toString())
                .activityType("userTask")
                .orderByHistoricActivityInstanceStartTime().asc()
                .list().stream()
                .map(FlowableWorkflowEngine::toStepInfo)
                .toList();
    }

    private static WorkflowStepInfo toStepInfo(HistoricActivityInstance a) {
        return new WorkflowStepInfo(a.getActivityId(), a.getActivityName(),
                a.getEndTime() != null,
                a.getStartTime() == null ? null : a.getStartTime().toInstant(),
                a.getEndTime() == null ? null : a.getEndTime().toInstant());
    }

    private Map<String, String> businessKeysFor(List<String> processInstanceIds) {
        if (processInstanceIds.isEmpty()) {
            return Map.of();
        }
        return runtimeService.createProcessInstanceQuery()
                .processInstanceIds(new HashSet<>(processInstanceIds))
                .list().stream()
                .filter(pi -> pi.getBusinessKey() != null)
                .collect(Collectors.toMap(ProcessInstance::getId, ProcessInstance::getBusinessKey));
    }

    private static WorkflowTask toWorkflowTask(Task task, Map<String, String> businessKeys) {
        return new WorkflowTask(task.getId(), task.getName(), task.getTaskDefinitionKey(),
                task.getProcessInstanceId(), businessKeys.get(task.getProcessInstanceId()),
                task.getCreateTime() == null ? null : task.getCreateTime().toInstant());
    }
}
