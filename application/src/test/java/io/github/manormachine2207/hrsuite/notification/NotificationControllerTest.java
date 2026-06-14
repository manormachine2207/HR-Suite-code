package io.github.manormachine2207.hrsuite.notification;

import io.github.manormachine2207.hrsuite.config.SecurityConfig;
import io.github.manormachine2207.hrsuite.shared.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class NotificationControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean NotificationService service;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void list_returns401_withoutToken() throws Exception {
        mvc.perform(get("/api/v1/notification")).andExpect(status().isUnauthorized());
    }

    @Test
    void unreadCount_returns200_forAuthenticated() throws Exception {
        when(service.unreadCount(any())).thenReturn(3L);
        mvc.perform(get("/api/v1/notification/unread-count")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_applicant"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void markRead_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/notification/" + id + "/read")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_applicant"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void markRead_maps_notFound_to404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new NotificationExceptions.NotFound("nope"))
                .when(service).markRead(eq(id), any());
        mvc.perform(post("/api/v1/notification/" + id + "/read")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_applicant"))))
                .andExpect(status().isNotFound());
    }
}
