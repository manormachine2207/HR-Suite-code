package io.github.manormachine2207.hrsuite.shared.secret;

import java.util.Optional;

/**
 * Resolves a config secret from a reference, never from the database (SDR-004). The
 * reference is the NAME of a secret (e.g. an environment variable); the value is
 * injected by the deployment (K8s secret, compose env, local .env) and never
 * persisted, logged, or returned over the API.
 *
 * <p>Lives in the OPEN {@code shared} module so every fachliche module
 * ({@code notification}, {@code platform}, {@code action}) shares ONE resolver — the
 * {@code *_secret_ref} contract (SDR-004) is the same everywhere. A deployment with a
 * secret manager can replace the bean with a Vault/KMS-backed resolver behind this
 * interface without touching callers.
 */
public interface SecretResolver {
    /** Resolves the secret named by {@code ref}, or empty if unset/blank. */
    Optional<String> resolve(String ref);

    /** True if a non-blank secret is available for {@code ref} (without revealing it). */
    default boolean isConfigured(String ref) {
        return ref != null && !ref.isBlank() && resolve(ref).isPresent();
    }
}
