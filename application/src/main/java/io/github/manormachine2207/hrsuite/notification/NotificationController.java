package io.github.manormachine2207.hrsuite.notification;

import io.github.manormachine2207.hrsuite.notification.dto.NotificationResponse;
import io.github.manormachine2207.hrsuite.notification.dto.UnreadCountResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * In-app notification API (ADR-017 Stufe 2). Recipient-own: every authenticated user
 * reads their own notifications (the bell). Tenant isolation via RLS (ADR-008); the
 * recipient is the JWT subject.
 */
@RestController
@RequestMapping("/api/v1/notification")
public class NotificationController {

    private static final String AUTH = "isAuthenticated()";

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(AUTH)
    public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.listOwn(jwt.getSubject()).stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/unread-count")
    @PreAuthorize(AUTH)
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(service.unreadCount(jwt.getSubject()));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize(AUTH)
    public ResponseEntity<Void> markRead(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.markRead(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @PreAuthorize(AUTH)
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
