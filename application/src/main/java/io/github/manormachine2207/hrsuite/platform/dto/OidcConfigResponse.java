package io.github.manormachine2207.hrsuite.platform.dto;

import io.github.manormachine2207.hrsuite.platform.OidcConfig;

import java.time.OffsetDateTime;

/**
 * Read model of the OIDC config (ADR-019 Stufe 2). Carries the client-secret
 * REFERENCE name + a {@code secretConfigured} flag — never the secret value
 * (SDR-004). {@code enabled} reflects stored intent; it is not yet enforced (Stufe 2b).
 */
public record OidcConfigResponse(
        String issuer,
        String clientId,
        String clientSecretRef,
        String scopes,
        String redirectUri,
        String tenantClaim,
        boolean secretConfigured,
        boolean enabled,
        OffsetDateTime updatedAt) {

    public static OidcConfigResponse from(OidcConfig c, boolean secretConfigured) {
        return new OidcConfigResponse(c.getIssuer(), c.getClientId(), c.getClientSecretRef(),
                c.getScopes(), c.getRedirectUri(), c.getTenantClaim(), secretConfigured,
                c.isEnabled(), c.getUpdatedAt());
    }
}
