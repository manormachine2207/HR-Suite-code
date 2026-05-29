package io.github.manormachine2207.hrsuite.shared.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Populates {@link TenantContext} from the authenticated JWT's {@code tenant_id}
 * claim, then clears it after the request (clear-after-use). Must run AFTER the
 * bearer-token authentication filter (wired via SecurityConfig.addFilterAfter),
 * so the JWT is already parsed. Requests without a tenant_id claim (e.g. the
 * platform-admin tenant API) leave the context empty — the aspect then issues no
 * SET and only non-RLS tables (tenant, system root) are reachable.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_CLAIM = "tenant_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            tenantIdFromSecurityContext().ifPresent(TenantContext::set);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Optional<UUID> tenantIdFromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String raw = jwt.getClaimAsString(TENANT_CLAIM);
            if (raw != null && !raw.isBlank()) {
                try {
                    return Optional.of(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                    // malformed tenant_id claim -> treat as no tenant (deny via empty RLS scope)
                }
            }
        }
        return Optional.empty();
    }
}
