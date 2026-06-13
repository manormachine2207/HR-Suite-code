package io.github.manormachine2207.hrsuite.tenant;

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

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class TenantControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TenantService service;

    @MockitoBean
    JwtDecoder jwtDecoder;   // satisfies oauth2ResourceServer wiring; bypassed by jwt() post-processor

    private static final String VALID_BODY = """
            {"code":"BIT","subdomain":"bit","displayName":{"de":"Bundesamt für Informatik"}}
            """;

    @Test
    void post_returns401_withoutToken() throws Exception {
        mvc.perform(post("/api/v1/tenant").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void post_returns403_whenNotPlatformAdmin() throws Exception {
        mvc.perform(post("/api/v1/tenant")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_user")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_returns201_whenPlatformAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any())).thenReturn(new Tenant(
                id, "BIT", Map.of("de", "Bundesamt für Informatik"), "bit",
                TenantStatus.ACTIVE, "de"));

        mvc.perform(post("/api/v1/tenant")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_platform-admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/v1/tenant/" + id)))
                .andExpect(jsonPath("$.code").value("BIT"))
                .andExpect(jsonPath("$.subdomain").value("bit"));
    }

    @Test
    void post_returns400_whenCodeBlank() throws Exception {
        String invalid = """
                {"code":"","subdomain":"bit","displayName":{"de":"x"}}
                """;
        mvc.perform(post("/api/v1/tenant")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_platform-admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
    }

    // ---- PATCH status (ADR-019 Stufe 1) -------------------------------------

    @Test
    void patchStatus_returns403_whenNotPlatformAdmin() throws Exception {
        mvc.perform(patch("/api/v1/tenant/" + UUID.randomUUID() + "/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_tenant-admin")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchStatus_returns200_andNewStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.changeStatus(eq(id), eq(TenantStatus.SUSPENDED))).thenReturn(new Tenant(
                id, "BIT", Map.of("de", "BIT"), "bit", TenantStatus.SUSPENDED, "de"));

        mvc.perform(patch("/api/v1/tenant/" + id + "/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_platform-admin")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void patchStatus_maps_illegalTransition_to422() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.changeStatus(any(), any())).thenThrow(
                new TenantExceptions.IllegalTransition(TenantStatus.ARCHIVED, TenantStatus.ACTIVE));

        mvc.perform(patch("/api/v1/tenant/" + id + "/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_platform-admin")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
