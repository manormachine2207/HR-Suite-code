package io.github.manormachine2207.hrsuite.platform;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default {@link SecretResolver} for the platform module: reads the secret from the
 * process environment (12-Factor, SDR-004 / BDR-007). Distinct class name from the
 * notification module's resolver so the two singleton beans don't collide. See
 * {@link SecretResolver} for the dedup note.
 */
@Component
public class PlatformEnvSecretResolver implements SecretResolver {
    @Override
    public Optional<String> resolve(String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        String value = System.getenv(ref);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }
}
