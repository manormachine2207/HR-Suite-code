package io.github.manormachine2207.hrsuite.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifiziert den ADR-008-Defense-in-depth-Check {@link RuntimeDbRoleCheck} gegen
 * eine echte PostgreSQL-Instanz: nur eine NOSUPERUSER/NOBYPASSRLS-Rolle darf den
 * Start passieren; Superuser und BYPASSRLS umgehen RLS und müssen den Start
 * abbrechen.
 */
@Testcontainers
class RuntimeDbRoleCheckIT {

    // withInitScript provisioniert 'hrsuite_app' (NOSUPERUSER NOBYPASSRLS); der
    // Testcontainers-Bootstrap-User ist ein Superuser (ADR-008, vgl. rls-it-init.sql).
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("db/rls-it-init.sql");

    @BeforeAll
    static void provisionBypassRole() {
        jdbcAs(POSTGRES.getUsername(), POSTGRES.getPassword())
                .execute("CREATE ROLE hrsuite_bypass WITH LOGIN PASSWORD 'dev' NOSUPERUSER BYPASSRLS");
    }

    @Test
    void failsFastWhenRoleIsSuperuser() {
        RuntimeDbRoleCheck check =
                new RuntimeDbRoleCheck(jdbcAs(POSTGRES.getUsername(), POSTGRES.getPassword()));

        assertThatThrownBy(() -> check.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-008")
                .hasMessageContaining("Superuser")
                .hasMessageContaining(POSTGRES.getUsername());
    }

    @Test
    void failsFastWhenRoleHasBypassRls() {
        RuntimeDbRoleCheck check = new RuntimeDbRoleCheck(jdbcAs("hrsuite_bypass", "dev"));

        assertThatThrownBy(() -> check.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-008")
                .hasMessageContaining("BYPASSRLS")
                .hasMessageContaining("hrsuite_bypass");
    }

    @Test
    void passesWhenRoleIsRestricted() {
        RuntimeDbRoleCheck check = new RuntimeDbRoleCheck(jdbcAs("hrsuite_app", "dev"));

        assertThatCode(() -> check.run(null)).doesNotThrowAnyException();
    }

    private static JdbcTemplate jdbcAs(String username, String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), username, password);
        ds.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(ds);
    }
}
