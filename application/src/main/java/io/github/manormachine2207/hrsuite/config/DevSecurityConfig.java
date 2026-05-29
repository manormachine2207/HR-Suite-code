package io.github.manormachine2207.hrsuite.config;

import io.github.manormachine2207.hrsuite.shared.tenant.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dev-only JwtDecoder. Two token shapes (no signature, no network):
 *   "dev-platform-admin"            -> roles:[platform-admin], no tenant_id
 *   "dev-<role>:<tenant-uuid>"      -> roles:[<role>], tenant_id:<uuid>
 * where <role> is one of tenant-admin | hr-designer | hr-reviewer | applicant.
 * The "<uuid>" lets tests create a tenant, capture its id, then mint a token
 * scoped to that tenant — exercising RLS end-to-end. Active solely under the
 * 'dev' profile; prod validates real OIDC tokens via OIDC_ISSUER_URI.
 */
@Configuration
@Profile("dev")
public class DevSecurityConfig {

    public static final String DEV_ADMIN_TOKEN = "dev-platform-admin";
    private static final Set<String> TENANT_ROLES =
            Set.of("tenant-admin", "hr-designer", "hr-reviewer", "applicant");

    @Bean
    JwtDecoder devJwtDecoder() {
        return token -> {
            Instant now = Instant.now();
            if (DEV_ADMIN_TOKEN.equals(token)) {
                return jwt(token, now, Map.of("sub", token, "roles", List.of("platform-admin")));
            }
            int colon = token.indexOf(':');
            if (token.startsWith("dev-") && colon > 4) {
                String role = token.substring(4, colon);
                String tenantId = token.substring(colon + 1);
                if (TENANT_ROLES.contains(role) && isUuid(tenantId)) {
                    return jwt(token, now, Map.of(
                            "sub", token,
                            "roles", List.of(role),
                            TenantContextFilter.TENANT_CLAIM, tenantId));
                }
            }
            throw new BadJwtException("dev decoder rejects token '" + token + "' "
                    + "(use 'dev-platform-admin' or 'dev-<role>:<tenant-uuid>')");
        };
    }

    private static Jwt jwt(String token, Instant now, Map<String, Object> claims) {
        return new Jwt(token, now, now.plusSeconds(3600), Map.of("alg", "none"), claims);
    }

    private static boolean isUuid(String s) {
        try {
            java.util.UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
