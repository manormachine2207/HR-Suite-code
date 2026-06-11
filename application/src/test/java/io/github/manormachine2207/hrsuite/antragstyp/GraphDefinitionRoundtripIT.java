package io.github.manormachine2207.hrsuite.antragstyp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.manormachine2207.hrsuite.HrSuiteApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test proving the opaque {@code graph_definition} round-trips byte-equivalent
 * via REST (ADR-012 SP2, Task 2). The graph JSON sent in POST /api/v1/antragstyp/{id}/versions
 * must come back unchanged from GET /api/v1/antragstyp/{id}/versions — Jackson tree equality.
 *
 * <p>Runs as NOSUPERUSER {@code hrsuite_app} so RLS binds (ADR-008).
 */
@SpringBootTest(classes = HrSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
class GraphDefinitionRoundtripIT {

    private static final String APP_ROLE = "hrsuite_app";
    private static final String APP_ROLE_PASSWORD = "dev";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("db/rls-it-init.sql");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_ROLE_PASSWORD);
    }

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    // ---- header helpers --------------------------------------------------

    private HttpHeaders admin() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth("dev-platform-admin");
        return h;
    }

    private HttpHeaders designer(String tenantId) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth("dev-hr-designer~" + tenantId);
        return h;
    }

    // ---- API helpers -----------------------------------------------------

    private String createTenant(String code, String subdomain) throws Exception {
        String body = """
                {"code":"%s","subdomain":"%s","displayName":{"de":"%s","fr":"%s","it":"%s","en":"%s"}}
                """.formatted(code, subdomain, code);
        ResponseEntity<String> r = rest.exchange("/api/v1/tenant", HttpMethod.POST,
                new HttpEntity<>(body, admin()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(r.getBody()).get("id").asText();
    }

    private String createAntragstyp(HttpHeaders h, String key) throws Exception {
        String body = """
                {"key":"%s","title":{"de":"%s","fr":"%s","it":"%s","en":"%s"}}
                """.formatted(key, key);
        ResponseEntity<String> r = rest.exchange("/api/v1/antragstyp", HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(r.getBody()).get("id").asText();
    }

    // ===== round-trip assertion ============================================

    @Test
    void graphDefinitionRoundTripsOpaquely() throws Exception {
        // 1. Tenant + designer headers
        String tenantId = createTenant("GRAPH", "graph");
        HttpHeaders des = designer(tenantId);

        // 2. Antragstyp
        String atId = createAntragstyp(des, "graph-smoke");

        // 3. Arbitrary graph JSON (non-trivial: nested objects, array)
        String graph = "{\"nodes\":["
                + "{\"id\":\"n1\",\"type\":\"START\",\"position\":{\"x\":10,\"y\":20},\"data\":{}},"
                + "{\"id\":\"n2\",\"type\":\"ACTION\",\"position\":{\"x\":200,\"y\":20},"
                + "\"data\":{\"key\":\"prov\",\"title\":{\"de\":\"P\"},\"ref\":\"r\",\"inputMapping\":{}}}"
                + "],\"edges\":[{\"id\":\"e1\",\"source\":\"n1\",\"target\":\"n2\"}]}";

        String versionBody = "{\"formDefinition\":{\"fields\":[]},\"workflowBpmn\":\"<bpmn/>\","
                + "\"sfActionBindings\":{},\"graphDefinition\":" + graph + "}";

        ResponseEntity<String> createResp = rest.exchange(
                "/api/v1/antragstyp/" + atId + "/versions", HttpMethod.POST,
                new HttpEntity<>(versionBody, des), String.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String vid = mapper.readTree(createResp.getBody()).get("id").asText();
        assertThat(vid).isNotBlank();

        // 4. Read back -> graphDefinition must equal the sent JSON tree (opaque round-trip)
        ResponseEntity<String> listResp = rest.exchange(
                "/api/v1/antragstyp/" + atId + "/versions", HttpMethod.GET,
                new HttpEntity<>(des), String.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        var stored = mapper.readTree(listResp.getBody()).get(0).get("graphDefinition");
        assertThat(stored)
                .as("graphDefinition stored in DB and returned via REST must equal the sent JSON tree")
                .isEqualTo(mapper.readTree(graph));
    }
}
