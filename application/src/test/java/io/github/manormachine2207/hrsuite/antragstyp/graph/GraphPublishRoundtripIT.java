package io.github.manormachine2207.hrsuite.antragstyp.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.manormachine2207.hrsuite.HrSuiteApplication;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-012 SP1 end-to-end: ein im Canvas-Format gespeicherter Graph (START→FORM→
 * XOR→2 ENDs) kompiliert beim Publish zu deploybarem BPMN, die Instanz startet,
 * der XOR routet nach Bedingung. Ein invalider Graph antwortet 422 und deployt nichts.
 */
@SpringBootTest(classes = HrSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
class GraphPublishRoundtripIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("db/rls-it-init.sql");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "hrsuite_app");
        r.add("spring.datasource.password", () -> "dev");
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String I18N = "{\"de\":\"T\",\"fr\":\"T\",\"it\":\"T\",\"en\":\"T\"}";

    @Autowired TestRestTemplate rest;
    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;

    private HttpHeaders token(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(bearer);
        return h;
    }

    @Test
    void graphPublishesCompilesAndRoutesXor() throws Exception {
        String tenantId = JSON.readTree(rest.exchange("/api/v1/tenant", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"GRAPHIT\",\"subdomain\":\"graphit\","
                        + "\"displayName\":{\"de\":\"Graph IT\"}}", token("dev-platform-admin")), String.class)
                .getBody()).get("id").asText();
        HttpHeaders h = token("dev-hr-designer~" + tenantId);

        String atId = JSON.readTree(rest.exchange("/api/v1/antragstyp", HttpMethod.POST,
                new HttpEntity<>("{\"key\":\"graphflow\",\"title\":" + I18N + "}", h), String.class)
                .getBody()).get("id").asText();

        String versionBody = """
                {
                  "formDefinition": {"fields": [{"key":"grund","type":"TEXT","required":true,
                    "label":%s}]},
                  "sfActionBindings": {},
                  "graphDefinition": {
                    "nodes": [
                      {"id":"n1","type":"START","position":{"x":0,"y":0},"data":{}},
                      {"id":"n2","type":"FORM","position":{"x":100,"y":0},
                       "data":{"key":"erfassen","title":{"de":"Erfassen"}}},
                      {"id":"n3","type":"XOR","position":{"x":200,"y":0},
                       "data":{"key":"entscheid","title":{"de":"Entscheid"}}},
                      {"id":"n4","type":"END","position":{"x":300,"y":-50},"data":{}},
                      {"id":"n5","type":"END","position":{"x":300,"y":50},"data":{}}
                    ],
                    "edges": [
                      {"id":"e1","source":"n1","target":"n2"},
                      {"id":"e2","source":"n2","target":"n3"},
                      {"id":"e3","source":"n3","target":"n4","label":"ja",
                       "condition":"entscheid_outcome == 'approve'"},
                      {"id":"e4","source":"n3","target":"n5","label":"sonst"}
                    ]
                  }
                }
                """.formatted(I18N);
        String versionId = JSON.readTree(rest.exchange("/api/v1/antragstyp/" + atId + "/versions",
                HttpMethod.POST, new HttpEntity<>(versionBody, h), String.class).getBody())
                .get("id").asText();

        var publishResp = rest.exchange("/api/v1/antragstyp/versions/" + versionId + "/publish",
                HttpMethod.POST, new HttpEntity<>(h), String.class);
        assertThat(publishResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        String versJson = rest.exchange("/api/v1/antragstyp/" + atId + "/versions",
                HttpMethod.GET, new HttpEntity<>(h), String.class).getBody();
        String storedBpmn = JSON.readTree(versJson).get(0).get("workflowBpmn").asText();
        assertThat(storedBpmn).contains("<exclusiveGateway id=\"entscheid\"")
                .contains("${execution.getVariable('entscheid_outcome') == 'approve'}");
        String procKey = JSON.readTree(versJson).get(0).get("processDefinitionKey").asText();

        TenantContext.set(UUID.fromString(tenantId));
        try {
            runtimeService.createProcessInstanceBuilder()
                    .processDefinitionKey(procKey)
                    .tenantId(tenantId)
                    .start();
            var formTasks = taskService.createTaskQuery()
                    .processDefinitionKey(procKey).taskDefinitionKey("erfassen").list();
            assertThat(formTasks).as("FORM userTask created on start").hasSize(1);
            // route over the XOR default branch (no outcome variable set)
            taskService.complete(formTasks.get(0).getId());
            assertThat(runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey(procKey).count())
                    .as("instance ended via XOR default branch").isZero();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void invalidGraphIsRejectedWith422AndNothingDeploys() throws Exception {
        String tenantId = JSON.readTree(rest.exchange("/api/v1/tenant", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"GRAPHIT2\",\"subdomain\":\"graphit2\","
                        + "\"displayName\":{\"de\":\"Graph IT 2\"}}", token("dev-platform-admin")), String.class)
                .getBody()).get("id").asText();
        HttpHeaders h = token("dev-hr-designer~" + tenantId);

        String atId = JSON.readTree(rest.exchange("/api/v1/antragstyp", HttpMethod.POST,
                new HttpEntity<>("{\"key\":\"brokengraph\",\"title\":" + I18N + "}", h), String.class)
                .getBody()).get("id").asText();

        // FORM node is a dead end and there is no END node
        String versionBody = """
                {
                  "formDefinition": {"fields": []},
                  "sfActionBindings": {},
                  "graphDefinition": {
                    "nodes": [
                      {"id":"n1","type":"START","position":{"x":0,"y":0},"data":{}},
                      {"id":"n2","type":"FORM","position":{"x":100,"y":0},
                       "data":{"key":"erfassen","title":{"de":"E"}}}
                    ],
                    "edges": [{"id":"e1","source":"n1","target":"n2"}]
                  }
                }
                """;
        String versionId = JSON.readTree(rest.exchange("/api/v1/antragstyp/" + atId + "/versions",
                HttpMethod.POST, new HttpEntity<>(versionBody, h), String.class).getBody())
                .get("id").asText();

        var publishResp = rest.exchange("/api/v1/antragstyp/versions/" + versionId + "/publish",
                HttpMethod.POST, new HttpEntity<>(h), String.class);
        assertThat(publishResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(publishResp.getBody()).contains("END");

        // the version must still be a publishable draft, nothing demoted/deployed
        String versJson = rest.exchange("/api/v1/antragstyp/" + atId + "/versions",
                HttpMethod.GET, new HttpEntity<>(h), String.class).getBody();
        assertThat(JSON.readTree(versJson).get(0).get("status").asText()).isEqualTo("DRAFT");
    }
}
