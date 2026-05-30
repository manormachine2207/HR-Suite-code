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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression cover for the ADR-009 "exactly one PUBLISHED major per Antragstyp"
 * invariant under concurrency. Two designers (well, two HTTP requests) publish two
 * distinct DRAFT majors of the same antragstyp at the same time; the per-antragstyp
 * advisory lock in {@link AntragsTypService#publish} must serialize them so that the
 * loser is demoted instead of leaving two PUBLISHED majors behind.
 *
 * <p>Harness mirrors {@code AntragsTypRlsIT}: the app connects as the NOSUPERUSER
 * {@code hrsuite_app} role so RLS (ADR-008) actually binds, and the locking
 * transaction keeps the {@code app.tenant_id} GUC set throughout.
 */
@SpringBootTest(classes = HrSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
class AntragsTypPublishConcurrencyIT {

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

    // ---- header / API helpers (mirrors AntragsTypRlsIT) -------------------
    private HttpHeaders admin() {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth("dev-platform-admin");
        return h;
    }

    private HttpHeaders designer(String tenantId) {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth("dev-hr-designer~" + tenantId);
        return h;
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

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

    private String createDraftMajor(HttpHeaders h, String antragstypId) throws Exception {
        String body = """
                {"formDefinition":{"fields":[
                  {"key":"a","type":"TEXT","required":true,"label":{"de":"A"}}
                ]},"workflowBpmn":"<bpmn/>","sfActionBindings":{}}
                """;
        ResponseEntity<String> r = rest.exchange("/api/v1/antragstyp/" + antragstypId + "/versions",
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(r.getBody()).get("id").asText();
    }

    // ===== concurrent publish serialization ===============================
    @Test
    void concurrentPublishesLeaveExactlyOnePublishedMajor() throws Exception {
        String tenant = createTenant("CONC", "conc");
        HttpHeaders h = designer(tenant);
        String atId = createAntragsTyp(h, "concurrency");
        // Two distinct DRAFT majors (major 1 and major 2). With no prior published major,
        // unsynchronized publishes both observe "none published" and both promote -> the
        // exact two-PUBLISHED outcome ADR-009 forbids.
        String v1 = createDraftMajor(h, atId);
        String v2 = createDraftMajor(h, atId);

        List<String> versionIds = List.of(v1, v2);
        ExecutorService pool = Executors.newFixedThreadPool(versionIds.size());
        CyclicBarrier startLine = new CyclicBarrier(versionIds.size());
        List<Future<Integer>> statuses = new ArrayList<>();
        try {
            for (String vid : versionIds) {
                statuses.add(pool.submit(() -> {
                    startLine.await(10, TimeUnit.SECONDS); // fire both publishes together
                    ResponseEntity<String> r = rest.exchange(
                            "/api/v1/antragstyp/versions/" + vid + "/publish",
                            HttpMethod.POST, new HttpEntity<>(h), String.class);
                    return r.getStatusCode().value();
                }));
            }
            for (Future<Integer> s : statuses) {
                // Both succeed: the lock loser still publishes, after demoting the winner.
                assertThat(s.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        ResponseEntity<String> versions = rest.exchange("/api/v1/antragstyp/" + atId + "/versions",
                HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(versions.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode arr = mapper.readTree(versions.getBody());

        long published = 0;
        String publishedVersionId = null;
        for (JsonNode v : arr) {
            if ("PUBLISHED".equals(v.get("status").asText())) {
                published++;
                publishedVersionId = v.get("id").asText();
            }
        }
        assertThat(published).as("exactly one PUBLISHED major remains (ADR-009)").isEqualTo(1);

        // The parent type points at the surviving published major.
        ResponseEntity<String> type = rest.exchange("/api/v1/antragstyp/" + atId, HttpMethod.GET,
                new HttpEntity<>(h), String.class);
        JsonNode typeNode = mapper.readTree(type.getBody());
        assertThat(typeNode.get("status").asText()).isEqualTo("LIVE");
        assertThat(typeNode.get("currentVersionId").asText()).isEqualTo(publishedVersionId);
    }
}
