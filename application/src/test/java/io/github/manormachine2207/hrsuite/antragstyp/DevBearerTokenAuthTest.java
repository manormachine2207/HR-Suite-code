package io.github.manormachine2207.hrsuite.antragstyp;

import io.github.manormachine2207.hrsuite.config.DevSecurityConfig;
import io.github.manormachine2207.hrsuite.config.MethodSecurityConfig;
import io.github.manormachine2207.hrsuite.config.SecurityConfig;
import io.github.manormachine2207.hrsuite.shared.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the dev bearer-token auth path through the REAL DefaultBearerTokenResolver
 * and the REAL {@link DevSecurityConfig} decoder. The controller slice tests inject
 * authentication via the jwt() post-processor and therefore never see the resolver's
 * RFC 6750 charset check — this test closes that gap. Regression guard for the
 * colon-separator bug: ':' is not a legal bearer-token character, so the resolver
 * rejected "dev-&lt;role&gt;:&lt;uuid&gt;" as malformed (401) before the decoder ran. The dev
 * token uses a '~' separator instead.
 */
@WebMvcTest(AntragsTypController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, ApiExceptionHandler.class, DevSecurityConfig.class})
@ActiveProfiles("dev")
class DevBearerTokenAuthTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AntragsTypService service;   // real DevSecurityConfig JwtDecoder is used (not mocked)

    @Test
    void tildeTokenAuthenticatesAndAuthorizesHrDesigner() throws Exception {
        when(service.listDefinitions()).thenReturn(List.of());
        mvc.perform(get("/api/v1/antragstyp")
                        .header("Authorization", "Bearer dev-hr-designer~" + UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    void colonTokenIsRejectedAsMalformedByResolver() throws Exception {
        mvc.perform(get("/api/v1/antragstyp")
                        .header("Authorization", "Bearer dev-hr-designer:" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminTokenAuthenticatesButLacksAntragstypReadRole() throws Exception {
        mvc.perform(get("/api/v1/antragstyp")
                        .header("Authorization", "Bearer dev-platform-admin"))
                .andExpect(status().isForbidden());
    }
}
