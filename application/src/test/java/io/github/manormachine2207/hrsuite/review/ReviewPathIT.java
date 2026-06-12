package io.github.manormachine2207.hrsuite.review;

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
 * ADR-013 end-to-end: publizierter Flow (FORM→APPROVAL) → Antrag submit → Reviewer
 * sieht die Tasks (inkl. candidate-group-loser FORM-Task), completed sie über die
 * API — Antragsstatus wandert SUBMITTED → IN_REVIEW → APPROVED. Applicant bleibt
 * draussen (403).
 */
@SpringBootTest(classes = HrSuiteApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
class ReviewPathIT {

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

    private HttpHeaders token(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(bearer);
        return h;
    }

    @Test
    void reviewerCompletesFormAndApprovalAndAntragBecomesApproved() throws Exception {
        // Tenant + publizierter Typ mit FORM→APPROVAL-Flow
        String tenantId = JSON.readTree(rest.exchange("/api/v1/tenant", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"REVIT\",\"subdomain\":\"revit\","
                        + "\"displayName\":{\"de\":\"Review IT\"}}", token("dev-platform-admin")), String.class)
                .getBody()).get("id").asText();
        HttpHeaders designer = token("dev-hr-designer~" + tenantId);
        HttpHeaders reviewer = token("dev-hr-reviewer~" + tenantId);
        HttpHeaders applicant = token("dev-applicant~" + tenantId);

        String atId = JSON.readTree(rest.exchange("/api/v1/antragstyp", HttpMethod.POST,
                new HttpEntity<>("{\"key\":\"reviewflow\",\"title\":" + I18N + "}", designer), String.class)
                .getBody()).get("id").asText();
        String versionBody = """
                {
                  "formDefinition": {"fields": [{"key":"grund","type":"TEXT","required":true,"label":%s}]},
                  "sfActionBindings": {},
                  "flowDefinition": {"steps": [
                      {"kind":"FORM","key":"erfassen","title":{"de":"Erfassen"}},
                      {"kind":"APPROVAL","key":"freigabe","title":{"de":"Freigabe"},
                       "assigneeRole":"hr-reviewer","outcomes":["approve","reject"]}
                  ]}
                }
                """.formatted(I18N);
        String versionId = JSON.readTree(rest.exchange("/api/v1/antragstyp/" + atId + "/versions",
                HttpMethod.POST, new HttpEntity<>(versionBody, designer), String.class).getBody())
                .get("id").asText();
        assertThat(rest.exchange("/api/v1/antragstyp/versions/" + versionId + "/publish",
                HttpMethod.POST, new HttpEntity<>(designer), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Antrag anlegen + einreichen (applicant)
        String antragId = JSON.readTree(rest.exchange("/api/v1/antrag", HttpMethod.POST,
                new HttpEntity<>("{\"antragstypId\":\"" + atId + "\",\"payload\":{\"grund\":\"Umzug\"}}",
                        applicant), String.class).getBody()).get("id").asText();
        JsonNode submitted = JSON.readTree(rest.exchange("/api/v1/antrag/" + antragId + "/submit",
                HttpMethod.POST, new HttpEntity<>(applicant), String.class).getBody());
        assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");

        // Applicant darf die Inbox nicht sehen
        assertThat(rest.exchange("/api/v1/task", HttpMethod.GET, new HttpEntity<>(applicant), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Reviewer sieht den FORM-Task (candidate-group-los) inkl. Antrag-Kontext
        JsonNode tasks = JSON.readTree(rest.exchange("/api/v1/task", HttpMethod.GET,
                new HttpEntity<>(reviewer), String.class).getBody());
        assertThat(tasks.size()).isEqualTo(1);
        assertThat(tasks.get(0).get("stepKey").asText()).isEqualTo("erfassen");
        assertThat(tasks.get(0).get("antragId").asText()).isEqualTo(antragId);

        // Detail traegt den Payload
        JsonNode detail = JSON.readTree(rest.exchange("/api/v1/task/" + tasks.get(0).get("taskId").asText(),
                HttpMethod.GET, new HttpEntity<>(reviewer), String.class).getBody());
        assertThat(detail.get("payload").get("grund").asText()).isEqualTo("Umzug");

        // FORM-Task ohne Outcome abschliessen → Prozess laeuft weiter → IN_REVIEW
        ResponseEntity<String> firstComplete = rest.exchange(
                "/api/v1/task/" + tasks.get(0).get("taskId").asText() + "/complete",
                HttpMethod.POST, new HttpEntity<>("{}", reviewer), String.class);
        assertThat(firstComplete.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(firstComplete.getBody()).get("antragStatus").asText()).isEqualTo("IN_REVIEW");

        // Fortschritt mid-flight (Cut D): erfassen abgeschlossen, freigabe offen
        JsonNode progress = JSON.readTree(rest.exchange("/api/v1/antrag/" + antragId + "/progress",
                HttpMethod.GET, new HttpEntity<>(applicant), String.class).getBody());
        assertThat(progress.get("steps").size()).isEqualTo(2);
        assertThat(progress.get("steps").get(0).get("stepKey").asText()).isEqualTo("erfassen");
        assertThat(progress.get("steps").get(0).get("completed").asBoolean()).isTrue();
        assertThat(progress.get("steps").get(1).get("stepKey").asText()).isEqualTo("freigabe");
        assertThat(progress.get("steps").get(1).get("completed").asBoolean()).isFalse();

        // Fremder Nutzer (Reviewer ist nicht Antragsteller) sieht den Fortschritt nicht
        assertThat(rest.exchange("/api/v1/antrag/" + antragId + "/progress",
                HttpMethod.GET, new HttpEntity<>(reviewer), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // APPROVAL-Task (candidate group hr-reviewer) mit deklariertem Outcome
        JsonNode approvalTasks = JSON.readTree(rest.exchange("/api/v1/task", HttpMethod.GET,
                new HttpEntity<>(reviewer), String.class).getBody());
        assertThat(approvalTasks.size()).isEqualTo(1);
        assertThat(approvalTasks.get(0).get("stepKey").asText()).isEqualTo("freigabe");
        assertThat(approvalTasks.get(0).get("allowedOutcomes")).isNotNull();

        String approvalTaskId = approvalTasks.get(0).get("taskId").asText();

        // Nicht deklariertes Outcome → 422, Status unveraendert
        assertThat(rest.exchange("/api/v1/task/" + approvalTaskId + "/complete", HttpMethod.POST,
                new HttpEntity<>("{\"outcome\":\"escalate\"}", reviewer), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // approve → Prozess endet → APPROVED
        ResponseEntity<String> approved = rest.exchange("/api/v1/task/" + approvalTaskId + "/complete",
                HttpMethod.POST, new HttpEntity<>("{\"outcome\":\"approve\"}", reviewer), String.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(approved.getBody()).get("antragStatus").asText()).isEqualTo("APPROVED");

        // Antragsteller sieht den Endstatus
        JsonNode finalAntrag = JSON.readTree(rest.exchange("/api/v1/antrag/" + antragId,
                HttpMethod.GET, new HttpEntity<>(applicant), String.class).getBody());
        assertThat(finalAntrag.get("status").asText()).isEqualTo("APPROVED");

        // Fortschritt bleibt nach Prozessende lesbar (Historie): beide Schritte abgeschlossen
        JsonNode finalProgress = JSON.readTree(rest.exchange("/api/v1/antrag/" + antragId + "/progress",
                HttpMethod.GET, new HttpEntity<>(applicant), String.class).getBody());
        assertThat(finalProgress.get("steps").size()).isEqualTo(2);
        assertThat(finalProgress.get("steps").get(1).get("completed").asBoolean()).isTrue();
        assertThat(finalProgress.get("steps").get(1).get("completedAt").asText()).isNotEmpty();
        assertThat(finalProgress.get("workflowProcessId").asText()).isNotEmpty();

        // Inbox ist leer
        assertThat(JSON.readTree(rest.exchange("/api/v1/task", HttpMethod.GET,
                new HttpEntity<>(reviewer), String.class).getBody()).size()).isZero();
    }
}
