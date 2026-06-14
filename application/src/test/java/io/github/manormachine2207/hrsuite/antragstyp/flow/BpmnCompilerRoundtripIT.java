package io.github.manormachine2207.hrsuite.antragstyp.flow;

import com.sun.net.httpserver.HttpServer;
import io.github.manormachine2207.hrsuite.HrSuiteApplication;
import io.github.manormachine2207.hrsuite.action.ActionItHarness;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that a FlowDefinition (FORM→APPROVAL→ACTION) compiles to deployable BPMN:
 * the compiled process starts in Flowable, the first userTask (reviewer) is created,
 * and when the reviewer completes with outcome "approve" the ACTION service-task
 * fires the n8n stub.
 */
@SpringBootTest(classes = HrSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
@Import(ActionItHarness.class)
class BpmnCompilerRoundtripIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("db/rls-it-init.sql");

    static HttpServer n8nStub;
    static int stubPort;
    static final AtomicInteger stubCalls = new AtomicInteger(0);

    @BeforeAll
    static void startStub() throws IOException {
        n8nStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        n8nStub.createContext("/webhook/", exchange -> {
            stubCalls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] out = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        n8nStub.start();
        stubPort = n8nStub.getAddress().getPort();
    }

    @AfterAll
    static void stopStub() {
        n8nStub.stop(0);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "hrsuite_app");
        r.add("spring.datasource.password", () -> "dev");
    }

    @Autowired TestRestTemplate rest;
    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired ActionItHarness harness;

    private HttpHeaders designerToken(String tenantId) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth("dev-hr-designer~" + tenantId);
        return h;
    }

    @Test
    void compiledFlowDeploysAndRunsFormApprovalAction() throws Exception {
        // 1. Create tenant
        String tenantRaw = rest.exchange("/api/v1/tenant", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"BPMNIT\",\"subdomain\":\"bpmnit\","
                        + "\"displayName\":{\"de\":\"BPMN IT\"}}", adminToken()), String.class)
                .getBody();
        String tenantId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(tenantRaw).get("id").asText();

        // 2. Seed n8n config (so ACTION step can call the stub)
        seedN8nConfig(tenantId, "http://127.0.0.1:" + stubPort);

        // 3. Create antragstyp
        HttpHeaders h = designerToken(tenantId);
        String atId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                rest.exchange("/api/v1/antragstyp", HttpMethod.POST,
                        new HttpEntity<>("{\"key\":\"urlaubsantrag\",\"title\":{\"de\":\"Urlaub\",\"fr\":\"Urlaub\",\"it\":\"Urlaub\",\"en\":\"Urlaub\"}}", h),
                        String.class).getBody()).get("id").asText();

        // 4. Create version WITH flowDefinition (FORM→APPROVAL→ACTION)
        String versionBody = """
                {
                  "formDefinition": {"fields": [{"key":"grund","type":"TEXT","required":true,
                    "label":{"de":"Grund","fr":"Grund","it":"Grund","en":"Grund"}}]},
                  
                  "sfActionBindings": {},
                  "flowDefinition": {
                    "steps": [
                      {"kind":"FORM","key":"antrag","title":{"de":"Antrag stellen","fr":"Antrag stellen","it":"Antrag stellen","en":"Antrag stellen"}},
                      {"kind":"APPROVAL","key":"review","title":{"de":"Freigabe","fr":"Freigabe","it":"Freigabe","en":"Freigabe"},
                       "assigneeRole":"hr-reviewer","outcomes":["approve","reject"]},
                      {"kind":"ACTION","key":"provision","title":{"de":"Konto anlegen","fr":"Konto anlegen","it":"Konto anlegen","en":"Konto anlegen"},
                       "ref":"provision-ad-account","inputMapping":{"upn":"test@example.com"}}
                    ]
                  }
                }
                """;
        String versionId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                rest.exchange("/api/v1/antragstyp/" + atId + "/versions", HttpMethod.POST,
                        new HttpEntity<>(versionBody, h), String.class).getBody()).get("id").asText();

        // 5. Publish → should compile FlowDefinition to BPMN and deploy to Flowable
        var publishResp = rest.exchange("/api/v1/antragstyp/versions/" + versionId + "/publish",
                HttpMethod.POST, new HttpEntity<>(h), String.class);
        assertThat(publishResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify the stored workflowBpmn is now the compiled BPMN (not the placeholder)
        String versJson = rest.exchange("/api/v1/antragstyp/" + atId + "/versions",
                HttpMethod.GET, new HttpEntity<>(h), String.class).getBody();
        String storedBpmn = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(versJson).get(0).get("workflowBpmn").asText();
        assertThat(storedBpmn).contains("<process").contains("antrag").contains("review").contains("provision");

        // 6. Start a process instance in tenant context
        TenantContext.set(UUID.fromString(tenantId));
        String procKey = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                rest.exchange("/api/v1/antragstyp/" + atId + "/versions", HttpMethod.GET,
                        new HttpEntity<>(h), String.class).getBody())
                .get(0).get("processDefinitionKey").asText();

        stubCalls.set(0);
        runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey(procKey)
                .tenantId(tenantId)
                .variable("antrag_grund", "Urlaub")
                .start();

        // 6b. The FORM step (antrag) is a userTask; complete it to advance the token to APPROVAL.
        var formTasks = taskService.createTaskQuery()
                .processDefinitionKey(procKey)
                .taskDefinitionKey("antrag")
                .list();
        assertThat(formTasks).as("FORM userTask (antrag) created on process start").hasSize(1);
        taskService.complete(formTasks.get(0).getId());

        // 7. After FORM completion, the reviewer userTask ("review") is created
        var tasks = taskService.createTaskQuery()
                .processDefinitionKey(procKey)
                .taskDefinitionKey("review")
                .list();
        assertThat(tasks).hasSize(1);

        // 8. Complete the reviewer task with approve → triggers ACTION service-task
        taskService.setVariable(tasks.get(0).getId(), "review_outcome", "approve");
        taskService.complete(tasks.get(0).getId());

        // 9. ACTION service-task should have called the n8n stub exactly once
        assertThat(stubCalls.get()).isEqualTo(1);

        TenantContext.clear();
    }

    private HttpHeaders adminToken() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth("dev-platform-admin");
        return h;
    }

    /**
     * Seeds the tenant_n8n_config row for the given tenant so the ACTION step can
     * call the stub. Mirrors ActionItHarness.seedConfig() from N8nActionConnectorIT:
     * sets TenantContext on this thread first (as TenantContextFilter does on the HTTP
     * path) so TenantContextAspect pushes the app.tenant_id GUC onto the transaction.
     */
    private void seedN8nConfig(String tenantId, String baseUrl) {
        UUID tid = UUID.fromString(tenantId);
        TenantContext.set(tid);
        try {
            harness.seedConfig(tid, baseUrl, "HRSUITE_N8N_HMAC_SECRET", List.of("provision-ad-account"));
        } finally {
            TenantContext.clear();
        }
    }
}
