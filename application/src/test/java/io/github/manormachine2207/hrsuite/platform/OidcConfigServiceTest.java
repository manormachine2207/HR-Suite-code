package io.github.manormachine2207.hrsuite.platform;

import io.github.manormachine2207.hrsuite.platform.dto.UpdateOidcConfigRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OidcConfigServiceTest {

    @Mock OidcConfigRepository repository;
    @Mock SecretResolver secretResolver;

    private OidcConfigService service;
    private OidcConfig config;

    @BeforeEach
    void setUp() {
        service = new OidcConfigService(repository, secretResolver);
        config = new OidcConfig();
        lenient().when(repository.findById(OidcConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void updateAppliesAllFields() {
        OidcConfig result = service.update(new UpdateOidcConfigRequest(
                "https://idp.example.ch", "hr-suite", "HRSUITE_OIDC_CLIENT_SECRET",
                "openid profile email", "https://app.example.ch/callback", "tenant", true));

        assertThat(result.getIssuer()).isEqualTo("https://idp.example.ch");
        assertThat(result.getClientId()).isEqualTo("hr-suite");
        assertThat(result.getClientSecretRef()).isEqualTo("HRSUITE_OIDC_CLIENT_SECRET");
        assertThat(result.getScopes()).isEqualTo("openid profile email");
        assertThat(result.getRedirectUri()).isEqualTo("https://app.example.ch/callback");
        assertThat(result.getTenantClaim()).isEqualTo("tenant");
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    void secretConfiguredDelegatesToResolver() {
        config.update("https://idp.example.ch", "hr-suite", "HRSUITE_OIDC_CLIENT_SECRET",
                null, null, null, false);
        when(secretResolver.isConfigured("HRSUITE_OIDC_CLIENT_SECRET")).thenReturn(true);

        assertThat(service.secretConfigured()).isTrue();
    }

    @Test
    void secretNotConfiguredWhenRefAbsent() {
        // default config carries no secret ref -> resolver reports not configured
        assertThat(service.secretConfigured()).isFalse();
    }
}
