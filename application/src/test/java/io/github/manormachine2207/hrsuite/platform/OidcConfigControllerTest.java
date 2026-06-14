package io.github.manormachine2207.hrsuite.platform;

import io.github.manormachine2207.hrsuite.config.SecurityConfig;
import io.github.manormachine2207.hrsuite.shared.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OidcConfigController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class OidcConfigControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean OidcConfigService service;
    @MockitoBean JwtDecoder jwtDecoder;

    private static SimpleGrantedAuthority role(String r) {
        return new SimpleGrantedAuthority("ROLE_" + r);
    }

    private OidcConfig config() {
        OidcConfig c = new OidcConfig();
        c.update("https://idp.example.ch", "hr-suite", "HRSUITE_OIDC_CLIENT_SECRET",
                "openid profile email", "https://app.example.ch/callback", "tenant", false);
        return c;
    }

    @Test
    void get_returns401_withoutToken() throws Exception {
        mvc.perform(get("/api/v1/platform/oidc")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_returns403_whenNotPlatformAdmin() throws Exception {
        mvc.perform(get("/api/v1/platform/oidc").with(jwt().authorities(role("tenant-admin"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_returns200_forPlatformAdmin_withoutSecretValue() throws Exception {
        when(service.get()).thenReturn(config());
        when(service.secretConfigured()).thenReturn(false);

        mvc.perform(get("/api/v1/platform/oidc").with(jwt().authorities(role("platform-admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("https://idp.example.ch"))
                .andExpect(jsonPath("$.clientSecretRef").value("HRSUITE_OIDC_CLIENT_SECRET"))
                .andExpect(jsonPath("$.secretConfigured").value(false))
                .andExpect(jsonPath("$.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void put_returns200_forPlatformAdmin() throws Exception {
        when(service.update(any())).thenReturn(config());
        when(service.secretConfigured()).thenReturn(false);
        String body = """
                {"issuer":"https://idp.example.ch","clientId":"hr-suite",
                 "clientSecretRef":"HRSUITE_OIDC_CLIENT_SECRET","scopes":"openid profile email",
                 "redirectUri":"https://app.example.ch/callback","tenantClaim":"tenant","enabled":false}
                """;
        mvc.perform(put("/api/v1/platform/oidc").with(jwt().authorities(role("platform-admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void put_returns400_whenEnabledWithoutIssuerAndClientId() throws Exception {
        String body = """
                {"issuer":"","clientId":"","enabled":true}
                """;
        mvc.perform(put("/api/v1/platform/oidc").with(jwt().authorities(role("platform-admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
