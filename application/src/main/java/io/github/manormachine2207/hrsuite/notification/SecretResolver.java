package io.github.manormachine2207.hrsuite.notification;

import java.util.Optional;

/**
 * Resolves a config secret from a reference, never from the database (SDR-004).
 * The reference is the NAME of a secret (e.g. an environment variable); the value
 * is injected by the deployment (K8s secret, compose env) and never persisted,
 * logged, or returned over the API.
 */
public interface SecretResolver {
    /** Resolves the secret named by {@code ref}, or empty if unset/blank. */
    Optional<String> resolve(String ref);

    /** True if a non-blank secret is available for {@code ref} (without revealing it). */
    default boolean isConfigured(String ref) {
        return ref != null && !ref.isBlank() && resolve(ref).isPresent();
    }
}
