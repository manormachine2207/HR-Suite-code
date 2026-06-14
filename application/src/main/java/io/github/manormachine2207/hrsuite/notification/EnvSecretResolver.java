package io.github.manormachine2207.hrsuite.notification;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default {@link SecretResolver}: reads the secret from the process environment
 * (12-Factor, SDR-004 / BDR-007). A deployment with a secret manager can replace
 * this bean with a Vault/KMS-backed resolver behind the same interface — the
 * {@code *_secret_ref} contract stays stable.
 */
@Component
public class EnvSecretResolver implements SecretResolver {
    @Override
    public Optional<String> resolve(String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        String value = System.getenv(ref);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }
}
