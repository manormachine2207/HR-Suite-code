package io.github.manormachine2207.hrsuite.notification;

import io.github.manormachine2207.hrsuite.config.SecurityConfig;
import io.github.manormachine2207.hrsuite.notification.dto.TestSendResponse;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SmtpRelayController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class SmtpRelayControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SmtpRelayService service;
    @MockitoBean JwtDecoder jwtDecoder;

    private static SimpleGrantedAuthority role(String r) {
        return new SimpleGrantedAuthority("ROLE_" + r);
    }

    private SmtpRelayConfig config() {
        SmtpRelayConfig c = new SmtpRelayConfig();
        c.update("mailpit", 1025, SmtpSecurity.NONE, "no-reply@hr-suite.local", null, null, null, false);
        return c;
    }

    @Test
    void get_returns401_withoutToken() throws Exception {
        mvc.perform(get("/api/v1/platform/smtp")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_returns403_whenNotPlatformAdmin() throws Exception {
        mvc.perform(get("/api/v1/platform/smtp").with(jwt().authorities(role("tenant-admin"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_returns200_forPlatformAdmin_withoutPasswordValue() throws Exception {
        when(service.get()).thenReturn(config());
        when(service.secretConfigured()).thenReturn(false);

        mvc.perform(get("/api/v1/platform/smtp").with(jwt().authorities(role("platform-admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value("mailpit"))
                .andExpect(jsonPath("$.secretConfigured").value(false))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void put_returns200_forPlatformAdmin() throws Exception {
        when(service.update(any())).thenReturn(config());
        when(service.secretConfigured()).thenReturn(false);
        String body = """
                {"host":"smtp.example.ch","port":587,"security":"STARTTLS",
                 "fromAddress":"hr@example.ch","passwordRef":"HRSUITE_SMTP_PASSWORD","enabled":true}
                """;
        mvc.perform(put("/api/v1/platform/smtp").with(jwt().authorities(role("platform-admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void put_returns400_onInvalidFromAddress() throws Exception {
        String body = """
                {"host":"smtp.example.ch","port":587,"security":"STARTTLS",
                 "fromAddress":"not-an-email","enabled":true}
                """;
        mvc.perform(put("/api/v1/platform/smtp").with(jwt().authorities(role("platform-admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void test_returns200_withOutcome() throws Exception {
        when(service.testSend(anyString())).thenReturn(new TestSendResponse(true, "success"));
        mvc.perform(post("/api/v1/platform/smtp/test").with(jwt().authorities(role("platform-admin")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"admin@example.ch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.outcome").value("success"));
    }
}
