package io.github.manormachine2207.hrsuite.action;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Execution record + dead-letter for one workflow action (ADR-010 L2). RLS-protected. */
@Entity
@Table(name = "action_execution")
public class ActionExecution {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "process_instance_id", nullable = false, updatable = false, length = 256)
    private String processInstanceId;

    @Column(name = "step_key", nullable = false, updatable = false, length = 128)
    private String stepKey;

    @Column(name = "ref", nullable = false, length = 128)
    private String ref;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ActionStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ActionExecution() {
    }

    public ActionExecution(UUID tenantId, String processInstanceId, String stepKey, String ref) {
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.tenantId = tenantId;
        this.processInstanceId = processInstanceId;
        this.stepKey = stepKey;
        this.ref = ref;
        this.status = ActionStatus.PENDING;
        this.attempts = 0;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void markRunning() {
        this.status = ActionStatus.RUNNING;
    }

    public void recordAttempt(String error) {
        this.attempts++;
        this.lastError = error;
    }

    public void markSucceeded() {
        this.status = ActionStatus.SUCCEEDED;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = ActionStatus.FAILED;
        this.lastError = error;
    }

    public void markDead(String error) {
        this.status = ActionStatus.DEAD;
        this.lastError = error;
    }

    public UUID getId() {
        return id;
    }

    public ActionStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public String getRef() {
        return ref;
    }
}
