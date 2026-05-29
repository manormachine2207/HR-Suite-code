package io.github.manormachine2207.hrsuite.antragstyp;

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
 * Request-type aggregate root. Tenant-scoped (RLS-protected). Holds the stable
 * {@code key}, i18n title/description, the overall {@link AntragsTypStatus} and a
 * pointer to the currently published major ({@code currentVersionId}). The
 * concrete major snapshots live in {@link AntragsTypVersion}. Id is an
 * app-assigned UUID v7.
 */
@Entity
@Table(name = "antragstyp")
public class AntragsTyp {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "key", nullable = false, length = 128)
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "title", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description", columnDefinition = "jsonb")
    private Map<String, String> description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AntragsTypStatus status;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AntragsTyp() {
        // for JPA
    }

    public AntragsTyp(UUID id, UUID tenantId, String key,
                      Map<String, String> title, Map<String, String> description) {
        this.id = id;
        this.tenantId = tenantId;
        this.key = key;
        this.title = title == null ? null : new LinkedHashMap<>(title);
        this.description = description == null ? null : new LinkedHashMap<>(description);
        this.status = AntragsTypStatus.DRAFT;
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

    public void markLive(UUID publishedVersionId) {
        this.status = AntragsTypStatus.LIVE;
        this.currentVersionId = publishedVersionId;
    }

    public void setCurrentVersionId(UUID versionId) { this.currentVersionId = versionId; }

    /**
     * Sets the status directly. Intended for deprecation/archival lifecycle
     * transitions. For the DRAFT→LIVE promotion prefer {@link #markLive(UUID)},
     * which additionally sets {@code currentVersionId}; calling this method alone
     * for LIVE leaves the pointer unset.
     */
    public void setStatus(AntragsTypStatus status) { this.status = status; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getKey() { return key; }
    public Map<String, String> getTitle() {
        return title == null ? null : Collections.unmodifiableMap(title);
    }
    public Map<String, String> getDescription() {
        return description == null ? null : Collections.unmodifiableMap(description);
    }
    public AntragsTypStatus getStatus() { return status; }
    public UUID getCurrentVersionId() { return currentVersionId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
