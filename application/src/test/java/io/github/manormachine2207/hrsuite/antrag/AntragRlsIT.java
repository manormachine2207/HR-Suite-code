package io.github.manormachine2207.hrsuite.antrag;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Integration cover for the antrag module: ADR-009 submit/pin, RLS tenant isolation
 * (ADR-008), and the applicant-vs-tenant-read authorization split (04-Authorization-Model).
 * Mirrors AntragsTypRlsIT's harness (app connects as the NOSUPERUSER hrsuite_app role
 * so RLS actually binds).
 */
@SpringBootTest(classes = HrSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
class AntragRlsIT {

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

    // ---- header helpers ---------------------------------------------------
    private HttpHeaders admin() {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth("dev-platform-admin");
        return h;
    }

    private HttpHeaders token(String role, String tenantId) {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth("dev-" + role + "~" + tenantId);
        return h;
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // ---- API helpers ------------------------------------------------------
    private String createTenant(String code, String subdomain) throws Exception {
        String body = """
                {"code":"%s","subdomain":"%s","displayName":{"de":"%s"}}
                """.formatted(code, subdomain, code);
        ResponseEntity<String> r = rest.exchange("/api/v1/tenant", HttpMethod.POST,
                new HttpEntity<>(body, admin()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(r.getBody()).get("id").asText();
    }

    /** Creates an antragstyp with one published major and returns {antragstypId, versionId}. */
    private String[] publishedAntragstyp(String tenant, String key) throws Exception {
        HttpHeaders designer = token("hr-designer", tenant);
        ResponseEntity<String> at = rest.exchange("/api/v1/antragstyp", HttpMethod.POST,
                new HttpEntity<>("{\"key\":\"%s\",\"title\":{\"de\":\"%s\"}}".formatted(key, key), designer), String.class);
        assertThat(at.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String atId = mapper.readTree(at.getBody()).get("id").asText();

        String versionBody = """
                {"formDefinition":{"fields":[{"key":"grund","type":"TEXT","required":true,"label":{"de":"Grund"}}]},
                 "workflowBpmn":"<bpmn/>","sfActionBindings":{}}
                """;
        ResponseEntity<String> v = rest.exchange("/api/v1/antragstyp/" + atId + "/versions", HttpMethod.POST,
                new HttpEntity<>(versionBody, designer), String.class);
        assertThat(v.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String versionId = mapper.readTree(v.getBody()).get("id").asText();

        ResponseEntity<String> p = rest.exchange("/api/v1/antragstyp/versions/" + versionId + "/publish",
                HttpMethod.POST, new HttpEntity<>(designer), String.class);
        assertThat(p.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new String[]{atId, versionId};
    }

    private ResponseEntity<String> createAntrag(HttpHeaders applicant, String antragstypId) {
        return rest.exchange("/api/v1/antrag", HttpMethod.POST,
                new HttpEntity<>("{\"antragstypId\":\"%s\",\"payload\":{\"grund\":\"x\"}}".formatted(antragstypId), applicant),
                String.class);
    }

    // ===== 1. submit pins the published major =============================
    @Test
    void submitPinsPublishedMajorAndMinor() throws Exception {
        String tenant = createTenant("APIN", "apin");
        String[] at = publishedAntragstyp(tenant, "sonderurlaub");
        HttpHeaders applicant = token("applicant", tenant);

        ResponseEntity<String> created = createAntrag(applicant, at[0]);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode draft = mapper.readTree(created.getBody());
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("antragstypVersionId").isNull()).isTrue();   // not pinned while draft
        String antragId = draft.get("id").asText();

        ResponseEntity<String> submitted = rest.exchange("/api/v1/antrag/" + antragId + "/submit",
                HttpMethod.POST, new HttpEntity<>(applicant), String.class);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode s = mapper.readTree(submitted.getBody());
        assertThat(s.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(s.get("antragstypVersionId").asText()).isEqualTo(at[1]);   // pinned the published major
        assertThat(s.get("submittedMinor").asInt()).isEqualTo(0);
    }

    // ===== 2. RLS isolation across tenants ================================
    @Test
    void reviewerOfTenantBcannotSeeTenantAsAntrag() throws Exception {
        String tenantA = createTenant("ARLA", "arla");
        String tenantB = createTenant("ARLB", "arlb");
        String[] at = publishedAntragstyp(tenantA, "urlaub");
        createAntrag(token("applicant", tenantA), at[0]);   // one antrag in tenant A

        ResponseEntity<String> bList = rest.exchange("/api/v1/antrag/tenant", HttpMethod.GET,
                new HttpEntity<>(token("hr-reviewer", tenantB)), String.class);
        assertThat(bList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(bList.getBody())).isEmpty();              // RLS hides A's antrag from B

        ResponseEntity<String> aList = rest.exchange("/api/v1/antrag/tenant", HttpMethod.GET,
                new HttpEntity<>(token("hr-reviewer", tenantA)), String.class);
        assertThat(mapper.readTree(aList.getBody())).hasSize(1);             // A sees its own
    }

    // ===== 3. applicant may not read the tenant-wide list =================
    @Test
    void applicantCannotReadTenantList() throws Exception {
        String tenant = createTenant("ARBAC", "arbac");
        ResponseEntity<String> r = rest.exchange("/api/v1/antrag/tenant", HttpMethod.GET,
                new HttpEntity<>(token("applicant", tenant)), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ===== 4. cannot submit against an antragstyp with no published major =
    @Test
    void createDraftFailsWhenAntragstypNotPublished() throws Exception {
        String tenant = createTenant("ANOP", "anop");
        // antragstyp exists but is never published (only a draft major)
        HttpHeaders designer = token("hr-designer", tenant);
        ResponseEntity<String> at = rest.exchange("/api/v1/antragstyp", HttpMethod.POST,
                new HttpEntity<>("{\"key\":\"nodraft\",\"title\":{\"de\":\"x\"}}", designer), String.class);
        String atId = mapper.readTree(at.getBody()).get("id").asText();

        ResponseEntity<String> r = createAntrag(token("applicant", tenant), atId);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
