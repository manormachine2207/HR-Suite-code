package io.github.manormachine2207.hrsuite.workflow;

/**
 * Result of deploying an antragstyp major's BPMN to the engine: the Flowable
 * deployment id and the resolved process-definition key + version that an
 * {@code Antrag} starts an instance from (these are stored on the AntragsTypVersion,
 * ADR-009 §5).
 */
public record DeployedProcess(
        String deploymentId,
        String processDefinitionKey,
        int processDefinitionVersion) {
}
