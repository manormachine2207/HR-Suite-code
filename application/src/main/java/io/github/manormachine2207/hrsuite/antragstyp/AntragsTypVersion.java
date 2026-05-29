package io.github.manormachine2207.hrsuite.antragstyp;

import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
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
 * One immutable, pinnable MAJOR snapshot of a request-type definition (ADR-009
 * §1). One row per major ({@code UNIQUE(antragstyp_id, major)}); {@code minor} is
 * the in-place, backward-compatible counter. {@code formDefinition} is the
 * provisional JSON form model (see {@link FormDefinition}); {@code workflowBpmn}
 * + {@code sfActionBindings} carry the (opaque, this cut) workflow/SF binding,
 * whose change forces a new major. The Flowable deployment fields are populated
 * by the later workflow-engine-bridge cut.
 */
@Entity
@Table(name = "antragstyp_version")
public class AntragsTypVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "antragstyp_id", nullable = false, updatable = false)
    private UUID antragstypId;

    @Column(name = "major", nullable = false, updatable = false)
    private int major;

    @Column(name = "minor", nullable = false)
    private int minor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private VersionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_definition", nullable = false, columnDefinition = "jsonb")
    private FormDefinition formDefinition;

    @Column(name = "workflow_bpmn", columnDefinition = "text")
    private String workflowBpmn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sf_action_bindings", columnDefinition = "jsonb")
    private Map<String, Object> sfActionBindings;

    @Column(name = "workflow_deployment_id", length = 256)
    private String workflowDeploymentId;

    @Column(name = "process_definition_key", length = 256)
    private String processDefinitionKey;

    @Column(name = "process_definition_version")
    private Integer processDefinitionVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "minor_changelog", columnDefinition = "jsonb")
    private Map<String, Object> minorChangelog;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AntragsTypVersion() {
        // for JPA
    }

    public AntragsTypVersion(UUID id, UUID tenantId, UUID antragstypId, int major,
                             FormDefinition formDefinition, String workflowBpmn,
                             Map<String, Object> sfActionBindings) {
        this.id = id;
        this.tenantId = tenantId;
        this.antragstypId = antragstypId;
        this.major = major;
        this.minor = 0;
        this.status = VersionStatus.DRAFT;
        this.formDefinition = formDefinition;
        this.workflowBpmn = workflowBpmn;
        this.sfActionBindings = sfActionBindings == null ? null : new LinkedHashMap<>(sfActionBindings);
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

    /** Applies a backward-compatible in-place minor edit (ADR-009 §1). */
    public void applyMinorEdit(FormDefinition newDefinition, Map<String, Object> changelog) {
        this.formDefinition = newDefinition;
        this.minor = this.minor + 1;
        this.minorChangelog = changelog == null ? null : new LinkedHashMap<>(changelog);
    }

    /**
     * Replaces all content of a still-unpublished DRAFT major. No compatibility
     * constraints apply before first publish (nothing is pinned yet, ADR-009 §1).
     */
    public void replaceDraftContent(FormDefinition newDefinition, String newWorkflowBpmn,
                                    Map<String, Object> newSfActionBindings) {
        this.formDefinition = newDefinition;
        this.workflowBpmn = newWorkflowBpmn;
        this.sfActionBindings = newSfActionBindings == null ? null : new LinkedHashMap<>(newSfActionBindings);
    }

    public void publish(UUID publishedBy) {
        this.status = VersionStatus.PUBLISHED;
        this.publishedAt = OffsetDateTime.now();
        this.publishedBy = publishedBy;
    }

    /**
     * Sets the status directly. Intended for deprecation/archival lifecycle
     * transitions (e.g. PUBLISHED→DEPRECATED, PUBLISHED→ARCHIVED). For the
     * DRAFT→PUBLISHED promotion prefer {@link #publish(UUID)}, which additionally
     * sets {@code publishedAt} and {@code publishedBy}.
     */
    public void setStatus(VersionStatus status) { this.status = status; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAntragstypId() { return antragstypId; }
    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public VersionStatus getStatus() { return status; }
    public FormDefinition getFormDefinition() { return formDefinition; }
    public String getWorkflowBpmn() { return workflowBpmn; }
    public Map<String, Object> getSfActionBindings() {
        return sfActionBindings == null ? null : Collections.unmodifiableMap(sfActionBindings);
    }
    public Map<String, Object> getMinorChangelog() {
        return minorChangelog == null ? null : Collections.unmodifiableMap(minorChangelog);
    }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public UUID getPublishedBy() { return publishedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
