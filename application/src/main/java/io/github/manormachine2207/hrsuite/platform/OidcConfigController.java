package io.github.manormachine2207.hrsuite.platform;

import io.github.manormachine2207.hrsuite.platform.dto.OidcConfigResponse;
import io.github.manormachine2207.hrsuite.platform.dto.UpdateOidcConfigRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform OIDC config API (ADR-019 Stufe 2). Global, platform-admin-guarded via
 * SecurityConfig ({@code /api/v1/platform/**}). No secret value crosses this API
 * (SDR-004): GET returns the env-var reference + a {@code secretConfigured} flag.
 * Stufe 2 is configure-only — the stored {@code enabled} flag is not yet enforced.
 */
@RestController
@RequestMapping("/api/v1/platform/oidc")
public class OidcConfigController {

    private final OidcConfigService service;

    public OidcConfigController(OidcConfigService service) {
        this.service = service;
    }

    @GetMapping
    public OidcConfigResponse get() {
        return OidcConfigResponse.from(service.get(), service.secretConfigured());
    }

    @PutMapping
    public OidcConfigResponse update(@Valid @RequestBody UpdateOidcConfigRequest request) {
        OidcConfig updated = service.update(request);
        return OidcConfigResponse.from(updated, service.secretConfigured());
    }
}
