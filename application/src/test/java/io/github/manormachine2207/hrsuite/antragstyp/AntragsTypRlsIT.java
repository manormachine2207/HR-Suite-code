package io.github.manormachine2207.hrsuite.antragstyp;

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

@SpringBootTest(classes = HrSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
class AntragsTypRlsIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    // ---- token / header helpers ------------------------------------------
    private HttpHeaders admin() {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth("dev-platform-admin");
        return h;
    }

    private HttpHeaders designer(String tenantId) {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth("dev-hr-designer:" + tenantId);
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

    private String createAntragsTyp(HttpHeaders h, String key) throws Exception {
        String body = """
                {"key":"%s","title":{"de":"%s"}}
                """.formatted(key, key);
        ResponseEntity<String> r = rest.exchange("/api/v1/antragstyp", HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(r.getBody()).get("id").asText();
    }

    private String createTwoFieldMajor(HttpHeaders h, String antragstypId) throws Exception {
        String body = """
                {"formDefinition":{"fields":[
                  {"key":"a","type":"TEXT","required":true,"label":{"de":"A"}},
                  {"key":"b","type":"TEXT","required":true,"label":{"de":"B"}}
                ]},"workflowBpmn":"<bpmn/>","sfActionBindings":{}}
                """;
        ResponseEntity<String> r = rest.exchange("/api/v1/antragstyp/" + antragstypId + "/versions",
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(r.getBody()).get("id").asText();
    }

    private void publish(HttpHeaders h, String versionId) {
        ResponseEntity<String> r = rest.exchange("/api/v1/antragstyp/versions/" + versionId + "/publish",
                HttpMethod.POST, new HttpEntity<>(h), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ===== 1. RLS isolation ===============================================
    @Test
    void tenantBcannotSeeTenantAsAntragsTyp() throws Exception {
        String tenantA = createTenant("RLSA", "rlsa");
        String tenantB = createTenant("RLSB", "rlsb");

        createAntragsTyp(designer(tenantA), "urlaub-a");

        ResponseEntity<String> bList = rest.exchange("/api/v1/antragstyp", HttpMethod.GET,
                new HttpEntity<>(designer(tenantB)), String.class);
        assertThat(bList.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode bArray = mapper.readTree(bList.getBody());
        assertThat(bArray).isEmpty();                       // RLS hides A's rows entirely
        assertThat(bList.getBody()).doesNotContain("urlaub-a");

        ResponseEntity<String> aList = rest.exchange("/api/v1/antragstyp", HttpMethod.GET,
                new HttpEntity<>(designer(tenantA)), String.class);
        assertThat(aList.getBody()).contains("urlaub-a");   // A still sees its own
    }

    // ===== 2. classifier enforcement ======================================
    @Test
    void breakingInPlaceMinorEditReturns422() throws Exception {
        String tenant = createTenant("CLS", "cls");
        HttpHeaders h = designer(tenant);
        String atId = createAntragsTyp(h, "klassifizierer");
        String vId = createTwoFieldMajor(h, atId);
        publish(h, vId);

        // remove required field "b" in place -> breaking -> 422
        String breaking = """
                {"formDefinition":{"fields":[
                  {"key":"a","type":"TEXT","required":true,"label":{"de":"A"}}
                ]}}
                """;
        ResponseEntity<String> r = rest.exchange("/api/v1/antragstyp/versions/" + vId + "/minor",
                HttpMethod.PUT, new HttpEntity<>(breaking, h), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ===== 3. lifecycle + minor visibility ================================
    @Test
    void publishMarksTypeLiveAndMinorEditBumpsMinor() throws Exception {
        String tenant = createTenant("LIFE", "life");
        HttpHeaders h = designer(tenant);
        String atId = createAntragsTyp(h, "lifecycle");
        String vId = createTwoFieldMajor(h, atId);
        publish(h, vId);

        // type is LIVE and points at the published version
        ResponseEntity<String> typeResp = rest.exchange("/api/v1/antragstyp/" + atId, HttpMethod.GET,
                new HttpEntity<>(h), String.class);
        JsonNode type = mapper.readTree(typeResp.getBody());
        assertThat(type.get("status").asText()).isEqualTo("LIVE");
        assertThat(type.get("currentVersionId").asText()).isEqualTo(vId);

        // non-breaking in-place minor edit (label change) -> minor bumps to 1
        String minor = """
                {"formDefinition":{"fields":[
                  {"key":"a","type":"TEXT","required":true,"label":{"de":"A neu"}},
                  {"key":"b","type":"TEXT","required":true,"label":{"de":"B"}}
                ]}}
                """;
        ResponseEntity<String> edit = rest.exchange("/api/v1/antragstyp/versions/" + vId + "/minor",
                HttpMethod.PUT, new HttpEntity<>(minor, h), String.class);
        assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(edit.getBody()).get("minor").asInt()).isEqualTo(1);

        // visible on GET versions
        ResponseEntity<String> versions = rest.exchange("/api/v1/antragstyp/" + atId + "/versions",
                HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(mapper.readTree(versions.getBody()).get(0).get("minor").asInt()).isEqualTo(1);
    }

    // ===== guard: applicant may read, may not write =======================
    @Test
    void applicantCannotCreateAntragsTyp() throws Exception {
        String tenant = createTenant("RBAC", "rbac");
        HttpHeaders applicant = jsonHeaders();
        applicant.setBearerAuth("dev-applicant:" + tenant);
        String body = """
                {"key":"verboten","title":{"de":"x"}}
                """;
        ResponseEntity<String> r = rest.exchange("/api/v1/antragstyp", HttpMethod.POST,
                new HttpEntity<>(body, applicant), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
