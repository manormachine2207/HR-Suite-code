package io.github.manormachine2207.hrsuite.tenant;

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
import java.util.Map;
import java.util.UUID;

/**
 * Tenant aggregate root. System root entity — lives OUTSIDE PostgreSQL RLS
 * per ADR-008. The id is a time-ordered UUID v7 assigned by the application
 * (see TenantService), not the database.
 */
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 64, unique = true)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "display_name", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> displayName;

    @Column(name = "subdomain", nullable = false, length = 64, unique = true)
    private String subdomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TenantStatus status;

    @Column(name = "default_locale", nullable = false, length = 5)
    private String defaultLocale;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Tenant() {
        // for JPA
    }

    public Tenant(UUID id, String code, Map<String, String> displayName,
                  String subdomain, TenantStatus status, String defaultLocale) {
        this.id = id;
        this.code = code;
        this.displayName = displayName;
        this.subdomain = subdomain;
        this.status = status;
        this.defaultLocale = defaultLocale;
    }

    /**
     * Legal status transitions (ADR-019 Stufe 1). ARCHIVED is terminal; ONBOARDING
     * activates; ACTIVE/SUSPENDED toggle; any non-archived tenant can be archived.
     * A no-op (same status) is rejected so the caller gets an explicit 422, not a
     * silent success.
     */
    private static final java.util.Map<TenantStatus, java.util.Set<TenantStatus>> ALLOWED =
            java.util.Map.of(
                    TenantStatus.ONBOARDING, java.util.Set.of(TenantStatus.ACTIVE, TenantStatus.ARCHIVED),
                    TenantStatus.ACTIVE, java.util.Set.of(TenantStatus.SUSPENDED, TenantStatus.ARCHIVED),
                    TenantStatus.SUSPENDED, java.util.Set.of(TenantStatus.ACTIVE, TenantStatus.ARCHIVED),
                    TenantStatus.ARCHIVED, java.util.Set.of());

    /** Applies a status transition, enforcing the allowed-transition matrix. */
    public void changeStatus(TenantStatus target) {
        if (!ALLOWED.getOrDefault(this.status, java.util.Set.of()).contains(target)) {
            throw new TenantExceptions.IllegalTransition(this.status, target);
        }
        this.status = target;
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

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public Map<String, String> getDisplayName() { return displayName; }
    public String getSubdomain() { return subdomain; }
    public TenantStatus getStatus() { return status; }
    public String getDefaultLocale() { return defaultLocale; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
