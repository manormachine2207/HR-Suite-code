package io.github.manormachine2207.hrsuite.platform;

import java.util.Optional;

/**
 * Resolves a config secret from a reference, never from the database (SDR-004). The
 * reference is the NAME of a secret (e.g. an environment variable); the value is
 * injected by the deployment and never persisted, logged, or returned over the API.
 *
 * <p>Intentionally mirrors {@code notification.SecretResolver}: keeping a platform-
 * local copy avoids a cross-module dependency for this small cut (ADR-019-spec
 * Stufe 2). Dedup follow-up: extract a single resolver into {@code shared}.
 */
public interface SecretResolver {
    /** Resolves the secret named by {@code ref}, or empty if unset/blank. */
    Optional<String> resolve(String ref);

    /** True if a non-blank secret is available for {@code ref} (without revealing it). */
    default boolean isConfigured(String ref) {
        return ref != null && !ref.isBlank() && resolve(ref).isPresent();
    }
}
