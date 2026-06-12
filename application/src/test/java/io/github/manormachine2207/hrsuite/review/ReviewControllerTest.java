package io.github.manormachine2207.hrsuite.review;

import io.github.manormachine2207.hrsuite.config.MethodSecurityConfig;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, ApiExceptionHandler.class})
class ReviewControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReviewService service;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static SimpleGrantedAuthority role(String r) {
        return new SimpleGrantedAuthority("ROLE_" + r);
    }

    @Test
    void listReturns401WithoutToken() throws Exception {
        mvc.perform(get("/api/v1/task")).andExpect(status().isUnauthorized());
    }

    @Test
    void listReturns403ForApplicantAndHrDesigner() throws Exception {
        mvc.perform(get("/api/v1/task").with(jwt().authorities(role("applicant"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/task").with(jwt().authorities(role("hr-designer"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listReturns200ForHrReviewer() throws Exception {
        when(service.listOpenTasks(any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/task").with(jwt().authorities(role("hr-reviewer"))))
                .andExpect(status().isOk());
    }

    @Test
    void completeMapsInvalidOutcomeTo422() throws Exception {
        when(service.complete(anyString(), any(), any()))
                .thenThrow(new ReviewExceptions.Invalid("outcome 'x' is not declared"));
        mvc.perform(post("/api/v1/task/t-1/complete").with(jwt().authorities(role("tenant-admin")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void completeMapsUnknownTaskTo404() throws Exception {
        when(service.complete(anyString(), any(), any()))
                .thenThrow(new ReviewExceptions.NotFound("task not found"));
        mvc.perform(post("/api/v1/task/missing/complete").with(jwt().authorities(role("hr-reviewer")))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}
