package io.github.manormachine2207.hrsuite.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.manormachine2207.hrsuite.HrSuiteApplication;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end cover for the n8n action path (Cut A, ADR-010 L2): a deployed BPMN
 * {@code serviceTask} bound to {@code ${n8nActionDelegate}} drives
 * {@link ActionExecutionService} through {@link N8nActionConnector} against a local
 * HTTP stub standing in for n8n. Proves (1) the happy path fires the webhook exactly
 * once with a non-blank HMAC signature and lands SUCCEEDED, (2) a 5xx retries to the
 * configured max and dead-letters (DEAD), and (3) RLS isolates rows across tenants.
 *
 * <p>Harness mirrors {@code AntragRlsIT} / {@code AntragsTypPublishConcurrencyIT}: the
 * app connects as the NOSUPERUSER {@code hrsuite_app} role so RLS (ADR-008) binds, and
 * tenant rows are created through the real {@code POST /api/v1/tenant} API (satisfying
 * the FK). The GUC ({@code app.tenant_id}) is set by the production
 * {@code TenantContextAspect}: {@link ActionItHarness} is a {@code @Service}, so setting
 * {@link TenantContext} on the thread (what {@code TenantContextFilter} does per request)
 * before calling it pushes the GUC onto the same transaction that runs the synchronous
 * delegate — identical to how {@code AntragService.submit} starts a Flowable instance.
 */
@SpringBootTest(classes = HrSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
@Import(ActionItHarness.class)
class N8nActionConnectorIT {

    private static final String APP_ROLE = "hrsuite_app";
    private static final String APP_ROLE_PASSWORD = "dev";
    private static final String REF = "provision-ad-account";
    private static final String STEP_KEY = "callAction"; // serviceTask id == currentActivityId

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("db/rls-it-init.sql");

    // ---- local HTTP stub standing in for n8n ------------------------------
    static HttpServer stub;
    static final AtomicInteger callCount = new AtomicInteger();
    static final AtomicReference<String> lastSignature = new AtomicReference<>();
    static final AtomicInteger statusToReturn = new AtomicInteger(200);

    @BeforeAll
    static void startStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/webhook/", exchange -> {
            callCount.incrementAndGet();
            lastSignature.set(exchange.getRequestHeaders().getFirst("X-HRSuite-Signature"));
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusToReturn.get(), body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        stub.start();
    }

    @AfterAll
    static void stopStub() {
        if (stub != null) {
            stub.stop(0);
        }
    }

    @BeforeEach
    void resetStub() {
        callCount.set(0);
        lastSignature.set(null);
        statusToReturn.set(200);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_ROLE_PASSWORD);
        // Bound retry: a transient (5xx) action is attempted exactly twice, then DEAD.
        registry.add("hrsuite.action.max-attempts", () -> "2");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ActionItHarness harness;

    private final ObjectMapper mapper = new ObjectMapper();

    private static String stubBaseUrl() {
        return "http://127.0.0.1:" + stub.getAddress().getPort();
    }

    // ---- tenant creation via the real API (mirrors AntragRlsIT) -----------
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
     * Runs {@code work} with {@link TenantContext} set to {@code tenant} on this
     * thread — the same hook {@code TenantContextFilter} provides on the HTTP path —
     * so the {@code @Service} harness's aspect pushes the {@code app.tenant_id} GUC.
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

    // ===== 1. happy path: one webhook call, signed, SUCCEEDED =============
    @Test
    void serviceTaskCallsStubOnceSignedAndSucceeds() throws Exception {
        UUID tenantA = createTenant("ACTA", "acta");
        statusToReturn.set(200);

        inTenant(tenantA, () -> {
            harness.seedConfig(tenantA, stubBaseUrl(), "top-secret", List.of(REF));
            harness.deployProcess(tenantA);
        });

        String pi = inTenant(tenantA, () ->
                harness.startProcess(tenantA, Map.of("samAccountName", "j.doe")));

        assertThat(callCount.get()).as("stub invoked exactly once").isEqualTo(1);
        assertThat(lastSignature.get()).as("X-HRSuite-Signature present").isNotBlank();

        ActionExecution exec = inTenant(tenantA, () ->
                harness.findExecution(pi, STEP_KEY)).orElseThrow();
        assertThat(exec.getRef()).isEqualTo(REF);
        assertThat(exec.getStatus()).isEqualTo(ActionStatus.SUCCEEDED);
        assertThat(exec.getAttempts()).isEqualTo(1);
    }

    // ===== 2. 5xx retries to max then dead-letters (DEAD) ================
    @Test
    void serviceTaskRetriesAndDeadLettersOn500() throws Exception {
        UUID tenantB = createTenant("ACTB", "actb");
        statusToReturn.set(500);

        inTenant(tenantB, () -> {
            harness.seedConfig(tenantB, stubBaseUrl(), "top-secret", List.of(REF));
            harness.deployProcess(tenantB);
        });

        // The delegate raises BpmnError on DEAD; with no boundary it surfaces to start().
        AtomicReference<String> piRef = new AtomicReference<>();
        assertThatThrownBy(() -> inTenant(tenantB, () ->
                piRef.set(harness.startProcess(tenantB, Map.of("samAccountName", "j.roe")))))
                .isInstanceOf(org.flowable.engine.delegate.BpmnError.class);

        // The process instance id isn't returned on a BpmnError; locate the row by ref/status.
        assertThat(callCount.get()).as("stub invoked exactly max-attempts (2) times").isEqualTo(2);

        ActionExecution exec = inTenant(tenantB, () ->
                harness.findDeadExecution(REF)).orElseThrow();
        assertThat(exec.getStatus()).isEqualTo(ActionStatus.DEAD);
        assertThat(exec.getAttempts()).isEqualTo(2);
    }

    // ===== 3. RLS: tenant A's row is invisible under tenant B ============
    @Test
    void actionExecutionRowIsTenantIsolated() throws Exception {
        UUID tenantA = createTenant("RLSA", "rlsa");
        UUID tenantB = createTenant("RLSB", "rlsb");
        statusToReturn.set(200);

        inTenant(tenantA, () -> {
            harness.seedConfig(tenantA, stubBaseUrl(), "top-secret", List.of(REF));
            harness.deployProcess(tenantA);
        });
        String pi = inTenant(tenantA, () ->
                harness.startProcess(tenantA, Map.of("samAccountName", "iso")));

        // Visible to A.
        Optional<ActionExecution> seenByA = inTenant(tenantA, () -> harness.findExecution(pi, STEP_KEY));
        assertThat(seenByA).as("A sees its own action_execution").isPresent();

        // Not visible to B (RLS tenant_isolation).
        Optional<ActionExecution> seenByB = inTenant(tenantB, () -> harness.findExecution(pi, STEP_KEY));
        assertThat(seenByB).as("B cannot see A's action_execution (RLS)").isEmpty();
        assertThat(inTenant(tenantB, harness::countExecutionsVisible))
                .as("B's RLS scope shows no rows of A").isZero();
    }
}
