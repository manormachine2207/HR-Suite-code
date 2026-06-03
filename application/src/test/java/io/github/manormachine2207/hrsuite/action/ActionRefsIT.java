package io.github.manormachine2207.hrsuite.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.manormachine2207.hrsuite.HrSuiteApplication;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RLS integration test for {@code GET /api/v1/action/refs} (Cut C, ADR-008).
 * Proves tenant isolation end-to-end with the app connecting as the NOSUPERUSER
 * {@code hrsuite_app} role so RLS binds: tenant A sees only its own
 * {@code allowed_refs}; a tenant with no config gets {@code []}.
 *
 * <p>Mirrors {@code N8nActionConnectorIT}: the harness is a {@code @Service @Transactional}
 * so {@code TenantContextAspect} pushes {@code app.tenant_id} when {@link TenantContext}
 * is set on the calling thread (via the {@code inTenant} helper below).
 */
@SpringBootTest(classes = HrSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
@Import(ActionItHarness.class)
class ActionRefsIT {

    private static final String APP_ROLE = "hrsuite_app";
    private static final String APP_ROLE_PASSWORD = "dev";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("db/rls-it-init.sql");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_ROLE_PASSWORD);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ActionItHarness harness;

    private final ObjectMapper mapper = new ObjectMapper();

    // ---- helpers (mirror N8nActionConnectorIT) ----------------------------

    private HttpHeaders admin() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth("dev-platform-admin");
        return h;
    }

    private UUID createTenant(String code, String subdomain) throws Exception {
        String body = """
                {"code":"%s","subdomain":"%s","displayName":{"de":"%s"}}
                """.formatted(code, subdomain, code);
        ResponseEntity<String> r = rest.exchange("/api/v1/tenant", HttpMethod.POST,
                new HttpEntity<>(body, admin()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(mapper.readTree(r.getBody()).get("id").asText());
    }

    /**
     * Runs {@code work} with {@link TenantContext} set to {@code tenant} on this thread —
     * same as {@code TenantContextFilter} on the HTTP path — so the {@code @Service}
     * harness's aspect pushes {@code app.tenant_id} for the transaction.
     */
    private <T> T inTenant(UUID tenant, Supplier<T> work) {
        TenantContext.set(tenant);
        try {
            return work.get();
        } finally {
            TenantContext.clear();
        }
    }

    private void inTenant(UUID tenant, Runnable work) {
        inTenant(tenant, () -> {
            work.run();
            return null;
        });
    }

    private HttpHeaders designer(UUID tenantId) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth("dev-hr-designer~" + tenantId);
        return h;
    }

    // ===== RLS: tenant A sees its refs; tenant B (no config) sees [] =======

    @Test
    void refsAreTenantScoped() throws Exception {
        UUID tenantA = createTenant("REFSA", "refsa");
        UUID tenantB = createTenant("REFSB", "refsb");

        // Seed config for tenant A only — must run inside inTenant so TenantContextAspect
        // pushes the GUC for the @Transactional seedConfig call.
        inTenant(tenantA, () ->
                harness.seedConfig(tenantA, "http://n8n:5678", "secret",
                        List.of("provision-ad-account", "sync-payroll")));

        // Tenant A sees its two refs.
        String[] a = rest.exchange("/api/v1/action/refs", HttpMethod.GET,
                new HttpEntity<>(designer(tenantA)), String[].class).getBody();
        assertThat(a).containsExactly("provision-ad-account", "sync-payroll");

        // Tenant B has no config -> empty list (never A's data leaking through RLS).
        String[] b = rest.exchange("/api/v1/action/refs", HttpMethod.GET,
                new HttpEntity<>(designer(tenantB)), String[].class).getBody();
        assertThat(b).isEmpty();
    }
}
