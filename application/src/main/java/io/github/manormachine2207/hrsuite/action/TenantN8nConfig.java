package io.github.manormachine2207.hrsuite.action;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Per-tenant n8n endpoint config (ADR-010 L2). RLS-protected (ADR-008). */
@Entity
@Table(name = "tenant_n8n_config")
public class TenantN8nConfig {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(name = "hmac_secret", nullable = false, length = 256)
    private String hmacSecret;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_refs", nullable = false, columnDefinition = "jsonb")
    private List<String> allowedRefs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TenantN8nConfig() {
    }

    public TenantN8nConfig(UUID tenantId, String baseUrl, String hmacSecret, List<String> allowedRefs) {
        this.tenantId = tenantId;
        this.baseUrl = baseUrl;
        this.hmacSecret = hmacSecret;
        this.allowedRefs = allowedRefs;
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

    @SuppressWarnings("unused")
    public UUID getTenantId() {
        return tenantId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    public List<String> getAllowedRefs() {
        return allowedRefs == null ? List.of() : allowedRefs;
    }
}
