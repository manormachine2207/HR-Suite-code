package io.github.manormachine2207.hrsuite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dev-only JwtDecoder. Accepts ONLY the literal token "dev-platform-admin"
 * and returns a JWT with roles:[platform-admin] — no signature, no network.
 * Active solely under the 'dev' profile; prod uses OIDC_ISSUER_URI instead.
 */
@Configuration
@Profile("dev")
public class DevSecurityConfig {

    public static final String DEV_ADMIN_TOKEN = "dev-platform-admin";

    @Bean
    JwtDecoder devJwtDecoder() {
        return token -> {
            if (!DEV_ADMIN_TOKEN.equals(token)) {
                throw new BadJwtException("dev decoder accepts only the literal token '" + DEV_ADMIN_TOKEN + "'");
            }
            Instant now = Instant.now();
            return new Jwt(
                    token,
                    now,
                    now.plusSeconds(3600),
                    Map.of("alg", "none"),
                    Map.of("sub", DEV_ADMIN_TOKEN, "roles", List.of("platform-admin")));
        };
    }
}
