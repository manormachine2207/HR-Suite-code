package io.github.manormachine2207.hrsuite.antragstyp;

import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import io.github.manormachine2207.hrsuite.config.MethodSecurityConfig;
import io.github.manormachine2207.hrsuite.config.SecurityConfig;
import io.github.manormachine2207.hrsuite.shared.tenant.MissingTenantContextException;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AntragsTypController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, ApiExceptionHandler.class})
class AntragsTypControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AntragsTypService service;

    @MockitoBean
    JwtDecoder jwtDecoder;   // satisfies oauth2ResourceServer wiring; bypassed by jwt() post-processor

    private static final String CREATE_BODY = """
            {"key":"sonderurlaub","title":{"de":"Sonderurlaub"}}
            """;
    private static final String MINOR_BODY = """
            {"formDefinition":{"fields":[]}}
            """;

    private static SimpleGrantedAuthority role(String r) {
        return new SimpleGrantedAuthority("ROLE_" + r);
    }

    private static AntragsTyp sampleType(UUID id) {
        return new AntragsTyp(id, UUID.randomUUID(), "sonderurlaub", Map.of("de", "Sonderurlaub"), Map.of());
    }

    // ---- create definition (write.draft = hr-designer only) --------------
    @Test
    void createReturns401WithoutToken() throws Exception {
        mvc.perform(post("/api/v1/antragstyp").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReturns403ForHrReviewer() throws Exception {
        mvc.perform(post("/api/v1/antragstyp").with(jwt().authorities(role("hr-reviewer")))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReturns201ForHrDesigner() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.createDefinition(eq("sonderurlaub"), any(), any())).thenReturn(sampleType(id));

        mvc.perform(post("/api/v1/antragstyp").with(jwt().authorities(role("hr-designer")))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("sonderurlaub"));
    }

    @Test
    void createReturns403WhenNoTenantInContext() throws Exception {
        // Authenticated hr-designer, but token carried no usable tenant_id claim, so the
        // service hits an empty TenantContext (ADR-008). Must surface as 403, not 500.
        when(service.createDefinition(eq("sonderurlaub"), any(), any()))
                .thenThrow(new MissingTenantContextException("no tenant in context"));

        mvc.perform(post("/api/v1/antragstyp").with(jwt().authorities(role("hr-designer")))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    // ---- read (any role) --------------------------------------------------
    @Test
    void listReturns200ForApplicant() throws Exception {
        when(service.listDefinitions()).thenReturn(List.of());
        mvc.perform(get("/api/v1/antragstyp").with(jwt().authorities(role("applicant"))))
                .andExpect(status().isOk());
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getDefinition(id)).thenThrow(new AntragsTypExceptions.NotFound("nope"));
        mvc.perform(get("/api/v1/antragstyp/" + id).with(jwt().authorities(role("hr-reviewer"))))
                .andExpect(status().isNotFound());
    }

    // ---- publish (tenant-admin or hr-designer) ---------------------------
    @Test
    void publishReturns403ForHrReviewer() throws Exception {
        mvc.perform(post("/api/v1/antragstyp/versions/" + UUID.randomUUID() + "/publish")
                        .with(jwt().authorities(role("hr-reviewer"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishReturns200ForTenantAdmin() throws Exception {
        UUID atId = UUID.randomUUID();
        var v = new AntragsTypVersion(UUID.randomUUID(), UUID.randomUUID(), atId, 1,
                new FormDefinition(List.of()), "<bpmn/>", Map.of());
        when(service.publish(any(), any())).thenReturn(v);
        mvc.perform(post("/api/v1/antragstyp/versions/" + v.getId() + "/publish")
                        .with(jwt().authorities(role("tenant-admin"))))
                .andExpect(status().isOk());
    }

    // ---- breaking minor edit -> 422 (handler mapping) --------------------
    @Test
    void minorEditReturns422WhenBreaking() throws Exception {
        when(service.editInPlaceMinor(any(), any()))
                .thenThrow(new AntragsTypExceptions.BreakingChange("removed field"));
        mvc.perform(put("/api/v1/antragstyp/versions/" + UUID.randomUUID() + "/minor")
                        .with(jwt().authorities(role("hr-designer")))
                        .contentType(MediaType.APPLICATION_JSON).content(MINOR_BODY))
                .andExpect(status().isUnprocessableEntity());
    }
}
