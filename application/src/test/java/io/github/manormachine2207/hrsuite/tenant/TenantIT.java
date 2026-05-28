package io.github.manormachine2207.hrsuite.tenant;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")   // activates DevSecurityConfig mock decoder; datasource overridden below
@Testcontainers
class TenantIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    TestRestTemplate rest;

    private HttpHeaders adminHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth("dev-platform-admin");
        return h;
    }

    @Test
    void createThenFetchRoundTrip() {
        String body = """
                {"code":"BIT","subdomain":"bit","displayName":{"de":"Bundesamt","fr":"Office fédéral"}}
                """;
        ResponseEntity<String> created = rest.exchange("/api/v1/tenant", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("\"code\":\"BIT\"");
        assertThat(created.getBody()).contains("Office fédéral");   // jsonb round-trip incl. UTF-8

        ResponseEntity<String> list = rest.exchange("/api/v1/tenant", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("\"subdomain\":\"bit\"");
    }

    @Test
    void duplicateCodeReturns409() {
        String body = """
                {"code":"DUP","subdomain":"dup-a","displayName":{"de":"x"}}
                """;
        rest.exchange("/api/v1/tenant", HttpMethod.POST, new HttpEntity<>(body, adminHeaders()), String.class);

        String dup = """
                {"code":"DUP","subdomain":"dup-b","displayName":{"de":"y"}}
                """;
        ResponseEntity<String> second = rest.exchange("/api/v1/tenant", HttpMethod.POST,
                new HttpEntity<>(dup, adminHeaders()), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsWithoutToken() {
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/tenant", "{}", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
