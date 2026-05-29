package io.github.manormachine2207.hrsuite.shared.tenant;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private Jwt jwtWith(Map<String, Object> claims) {
        Instant now = Instant.now();
        return new Jwt("t", now, now.plusSeconds(60), Map.of("alg", "none"), claims);
    }

    @Test
    void setsTenantFromClaimDuringChainAndClearsAfter() throws Exception {
        UUID tenant = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwtWith(Map.of("sub", "u", "tenant_id", tenant.toString()))));

        AtomicReference<UUID> seenDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenDuringChain.set(TenantContext.get().orElse(null));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(seenDuringChain.get()).isEqualTo(tenant);   // visible inside the request
        assertThat(TenantContext.get()).isEmpty();             // cleared after
    }

    @Test
    void leavesContextEmptyWhenNoTenantClaim() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwtWith(Map.of("sub", "platform-admin"))));
        AtomicReference<Boolean> emptyDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> emptyDuringChain.set(TenantContext.get().isEmpty());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(emptyDuringChain.get()).isTrue();
    }

    @Test
    void ignoresNonJwtAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("u", "p"));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    void treatsInvalidUuidClaimAsNoTenant() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwtWith(Map.of("sub", "u",
                        TenantContextFilter.TENANT_CLAIM, "not-a-uuid"))));

        AtomicReference<Boolean> emptyDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> emptyDuringChain.set(TenantContext.get().isEmpty());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(emptyDuringChain.get()).isTrue();   // malformed claim swallowed, no tenant set
        assertThat(TenantContext.get()).isEmpty();      // cleared after
    }
}
