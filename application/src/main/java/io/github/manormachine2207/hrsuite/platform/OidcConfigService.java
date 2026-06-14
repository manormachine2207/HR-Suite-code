package io.github.manormachine2207.hrsuite.platform;

import io.github.manormachine2207.hrsuite.platform.dto.UpdateOidcConfigRequest;
import io.github.manormachine2207.hrsuite.shared.secret.SecretResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the global OIDC config (ADR-019 Stufe 2): read/update the
 * singleton. The client secret is never read here — only its env-var reference
 * (SDR-004). Stufe 2 stores the config; it does not enforce it at runtime (Stufe 2b).
 */
@Service
@Transactional
public class OidcConfigService {

    private final OidcConfigRepository repository;
    private final SecretResolver secretResolver;

    public OidcConfigService(OidcConfigRepository repository, SecretResolver secretResolver) {
        this.repository = repository;
        this.secretResolver = secretResolver;
    }

    @Transactional(readOnly = true)
    public OidcConfig get() {
        return repository.findById(OidcConfig.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "oidc_config singleton row is missing (migration 015 seeds it)"));
    }

    /** True if the client-secret env var named by the current config is set (without revealing it). */
    @Transactional(readOnly = true)
    public boolean secretConfigured() {
        return secretResolver.isConfigured(get().getClientSecretRef());
    }

    public OidcConfig update(UpdateOidcConfigRequest req) {
        OidcConfig config = get();
        config.update(req.issuer(), req.clientId(), req.clientSecretRef(), req.scopes(),
                req.redirectUri(), req.tenantClaim(), req.enabled());
        return repository.save(config);
    }
}
