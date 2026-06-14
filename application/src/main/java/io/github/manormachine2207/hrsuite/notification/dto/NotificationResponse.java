package io.github.manormachine2207.hrsuite.notification.dto;

import io.github.manormachine2207.hrsuite.notification.Notification;
import io.github.manormachine2207.hrsuite.notification.NotificationType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Read model of an in-app notification (ADR-017 Stufe 2); FE renders type+params via i18n. */
public record NotificationResponse(
        UUID id,
        NotificationType type,
        UUID antragId,
        Map<String, Object> params,
        boolean read,
        OffsetDateTime createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getAntragId(),
                n.getParams(), n.isRead(), n.getCreatedAt());
    }
}
