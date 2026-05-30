package io.github.manormachine2207.hrsuite.workflow;

import io.github.manormachine2207.hrsuite.HrSuiteApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Risk-gate spike for ADR-004: verifies the embedded Flowable engine boots inside the
 * full Spring context, creates its act_* schema as the NOSUPERUSER hrsuite_app role,
 * and round-trips a deploy → start-instance. Placeholder BPMN is substituted by the
 * default process. (Wiring into antragstyp publish / antrag submit follows once this is green.)
 */
@SpringBootTest(classes = HrSuiteApplication.class)
@ActiveProfiles("dev")
@Testcontainers
class WorkflowEngineIT {

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
    WorkflowEngine engine;

    @Test
    void deploysDefaultProcessAndStartsInstanceAsRestrictedRole() {
        UUID tenant = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        String key = "at_spike_v1";

        // Placeholder BPMN -> engine substitutes the minimal default process.
        DeployedProcess deployed = engine.deploy(tenant, key, "Spike", "<bpmn/>");
        assertThat(deployed.processDefinitionKey()).isEqualTo(key);
        assertThat(deployed.processDefinitionVersion()).isEqualTo(1);
        assertThat(deployed.deploymentId()).isNotBlank();

        String processInstanceId = engine.startInstance(tenant, key, "antrag-1", Map.of("foo", "bar"));
        assertThat(processInstanceId).isNotBlank();
    }
}
