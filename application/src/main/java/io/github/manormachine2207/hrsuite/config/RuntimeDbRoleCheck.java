package io.github.manormachine2207.hrsuite.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Defense-in-depth-Startup-Check (ADR-008): verifiziert, dass die Laufzeit-DB-Rolle
 * KEIN PostgreSQL-Superuser und KEINE BYPASSRLS-Rolle ist.
 *
 * <p>Row-Level Security isoliert Mandanten nur, solange die Verbindungsrolle weder
 * Superuser noch BYPASSRLS besitzt — beide umgehen RLS bedingungslos, auch das in
 * {@code 003-enable-rls-antragstyp.sql} gesetzte {@code FORCE ROW LEVEL SECURITY}.
 * Wird {@code DB_USERNAME} versehentlich auf den Bootstrap-Superuser
 * ({@code POSTGRES_USER}) statt auf die dedizierte {@code hrsuite_app}-Rolle gesetzt,
 * hört die Tenant-Isolation lautlos auf zu wirken: Queries liefern weiterhin
 * Ergebnisse, nur eben über alle Mandanten hinweg. Das Defaulting von
 * {@code spring.datasource.username} auf {@code hrsuite_app} (application.yml) deckt
 * den Bare-Defaults-Fall ab; dieser Check härtet zusätzlich gegen eine explizite
 * Fehlkonfiguration und bricht den Start ab, statt unsicher weiterzulaufen.
 *
 * <p>Nicht aktiv unter dem {@code dev}-Profil: dort verbindet sich die
 * {@code TenantIT} bewusst als Bootstrap-Superuser gegen die nicht-RLS
 * {@code tenant}-Systemtabelle, und die Laufzeit-Rolle ist umgebungskontrolliert
 * (Compose/IT setzen {@code hrsuite_app}). In Produktion läuft der Default-Profil
 * und damit dieser Check.
 */
@Component
@Profile("!dev")
public class RuntimeDbRoleCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RuntimeDbRoleCheck.class);

    private static final String ROLE_QUERY =
            "SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user";

    private final JdbcTemplate jdbc;

    RuntimeDbRoleCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        RoleFlags flags = jdbc.queryForObject(ROLE_QUERY, (rs, rowNum) -> new RoleFlags(
                rs.getString("rolname"),
                rs.getBoolean("rolsuper"),
                rs.getBoolean("rolbypassrls")));

        if (flags.superuser() || flags.bypassRls()) {
            String trait = flags.superuser() ? "ein Superuser" : "eine BYPASSRLS-Rolle";
            throw new IllegalStateException(
                    "ADR-008: Die Anwendung ist als DB-Rolle '" + flags.role() + "' verbunden, die "
                    + trait + " ist und PostgreSQL Row-Level Security bedingungslos umgeht — die "
                    + "Mandanten-Isolation wäre damit lautlos wirkungslos. DB_USERNAME muss auf eine "
                    + "NOSUPERUSER/NOBYPASSRLS-Rolle (z. B. 'hrsuite_app') zeigen. Start abgebrochen.");
        }

        log.info("ADR-008 DB-Rollen-Check OK: '{}' ist NOSUPERUSER/NOBYPASSRLS — RLS isoliert Mandanten.",
                flags.role());
    }

    private record RoleFlags(String role, boolean superuser, boolean bypassRls) {
    }
}
