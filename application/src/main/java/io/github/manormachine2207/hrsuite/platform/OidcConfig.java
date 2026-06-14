package io.github.manormachine2207.hrsuite.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Global OIDC configuration (ADR-019 Stufe 2). System-scope singleton (id=1, no RLS)
 * — one platform-wide identity provider, platform-admin-guarded at the API (SDR-001:
 * SSO is global, not per tenant). SDR-004: the client secret is NEVER stored here —
 * {@code clientSecretRef} names an environment variable resolved at runtime.
 *
 * <p><b>Stufe 2 stores but does NOT enforce.</b> {@code enabled} is persisted and
 * editable, but no resource-server validation or FE OAuth bootstrap reads it yet —
 * live enforcement is the security-focused Stufe-2b follow-up (Tenet 1).
 */
@Entity
@Table(name = "oidc_config")
public class OidcConfig {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Short id = SINGLETON_ID;

    @Column(name = "issuer", length = 512)
    private String issuer;

    @Column(name = "client_id", length = 255)
    private String clientId;

    /** Name of the env var holding the client secret (never the secret itself, SDR-004). */
    @Column(name = "client_secret_ref", length = 128)
    private String clientSecretRef;

    @Column(name = "scopes", length = 512)
    private String scopes;

    @Column(name = "redirect_uri", length = 512)
    private String redirectUri;

    /** Claim mapping a login to a tenant — security-critical for the global IdP (enforced in Stufe 2b). */
    @Column(name = "tenant_claim", length = 128)
    private String tenantClaim;

    /** Stored, but NOT enforced in Stufe 2 — activation is the Stufe-2b follow-up. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected OidcConfig() {
        // for JPA
    }

    /** Applies an admin edit. The client secret value is never touched — only its reference. */
    public void update(String issuer, String clientId, String clientSecretRef, String scopes,
                       String redirectUri, String tenantClaim, boolean enabled) {
        this.issuer = issuer;
        this.clientId = clientId;
        this.clientSecretRef = clientSecretRef;
        this.scopes = scopes;
        this.redirectUri = redirectUri;
        this.tenantClaim = tenantClaim;
        this.enabled = enabled;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Short getId() { return id; }
    public String getIssuer() { return issuer; }
    public String getClientId() { return clientId; }
    public String getClientSecretRef() { return clientSecretRef; }
    public String getScopes() { return scopes; }
    public String getRedirectUri() { return redirectUri; }
    public String getTenantClaim() { return tenantClaim; }
    public boolean isEnabled() { return enabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
