package io.github.manormachine2207.hrsuite.platform.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Body for PUT /api/v1/platform/oidc (ADR-019 Stufe 2). Carries the client-secret
 * REFERENCE name, never the secret value (SDR-004). All fields are optional while the
 * config is disabled; enabling requires at least an issuer and a client id.
 */
public record UpdateOidcConfigRequest(
        @Size(max = 512) String issuer,
        @Size(max = 255) String clientId,
        @Size(max = 128) String clientSecretRef,
        @Size(max = 512) String scopes,
        @Size(max = 512) String redirectUri,
        @Size(max = 128) String tenantClaim,
        boolean enabled) {

    /**
     * Guard: a config may only be stored {@code enabled} once it carries the minimum
     * to attempt a login (issuer + client id). Stufe 2 still does not enforce the flag
     * at runtime — this only prevents persisting a nonsensical "enabled but empty"
     * config.
     */
    @AssertTrue(message = "enabling OIDC requires issuer and clientId")
    public boolean isActivatableWhenEnabled() {
        if (!enabled) {
            return true;
        }
        return notBlank(issuer) && notBlank(clientId);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
