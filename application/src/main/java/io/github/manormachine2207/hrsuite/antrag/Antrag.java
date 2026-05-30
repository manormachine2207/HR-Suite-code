package io.github.manormachine2207.hrsuite.antrag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A concrete request instance (03-Domain-Model). Per ADR-009 a draft is NOT yet
 * pinned ({@code antragstypVersionId}/{@code submittedMinor} stay null); on
 * {@link #submit(UUID, int)} it pins the currently published major and records the
 * minor it was submitted against (reproducibility/audit) and runs to completion on
 * that pinned major.
 *
 * <p>{@code tenant_id} is RLS-protected (ADR-008). {@code antragstellerSubject} is the
 * JWT subject until the identity-sp cut maps it to a {@code User} id.
 */
@Entity
@Table(name = "antrag")
public class Antrag {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "antragstyp_id", nullable = false, updatable = false)
    private UUID antragstypId;

    @Column(name = "antragstyp_version_id")
    private UUID antragstypVersionId;

    @Column(name = "submitted_minor")
    private Integer submittedMinor;

    @Column(name = "migrated_from_version_id")
    private UUID migratedFromVersionId;

    @Column(name = "antragsteller_subject", nullable = false, updatable = false, length = 256)
    private String antragstellerSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AntragStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "workflow_process_id", length = 256)
    private String workflowProcessId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision_comment", columnDefinition = "jsonb")
    private Map<String, String> decisionComment;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Antrag() {
        // for JPA
    }

    /** Creates a new DRAFT request (not yet pinned to a major). */
    public Antrag(UUID id, UUID tenantId, UUID antragstypId, String antragstellerSubject,
                  Map<String, Object> payload) {
        this.id = id;
        this.tenantId = tenantId;
        this.antragstypId = antragstypId;
        this.antragstellerSubject = antragstellerSubject;
        this.status = AntragStatus.DRAFT;
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** Replaces the form payload of a still-unsubmitted DRAFT. */
    public void replaceDraftPayload(Map<String, Object> newPayload) {
        this.payload = newPayload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(newPayload);
    }

    /**
     * Pins the published major + the minor it was submitted against and moves the
     * request to SUBMITTED (ADR-009 §4). The Flowable process-instance start lands
     * with the workflow-engine-bridge cut; {@code workflowProcessId} stays null until then.
     */
    public void submit(UUID pinnedVersionId, int submittedMinor) {
        this.antragstypVersionId = pinnedVersionId;
        this.submittedMinor = submittedMinor;
        this.status = AntragStatus.SUBMITTED;
        this.submittedAt = OffsetDateTime.now();
    }

    /** Withdraws a DRAFT or SUBMITTED request. */
    public void cancel() {
        this.status = AntragStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAntragstypId() { return antragstypId; }
    public UUID getAntragstypVersionId() { return antragstypVersionId; }
    public Integer getSubmittedMinor() { return submittedMinor; }
    public UUID getMigratedFromVersionId() { return migratedFromVersionId; }
    public String getAntragstellerSubject() { return antragstellerSubject; }
    public AntragStatus getStatus() { return status; }
    public Map<String, Object> getPayload() {
        return payload == null ? null : Collections.unmodifiableMap(payload);
    }
    public String getWorkflowProcessId() { return workflowProcessId; }
    public Map<String, String> getDecisionComment() {
        return decisionComment == null ? null : Collections.unmodifiableMap(decisionComment);
    }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
