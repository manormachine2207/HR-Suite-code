package io.github.manormachine2207.hrsuite.action;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActionExecutionRepository extends JpaRepository<ActionExecution, UUID> {
    Optional<ActionExecution> findByProcessInstanceIdAndStepKey(String processInstanceId, String stepKey);
}
