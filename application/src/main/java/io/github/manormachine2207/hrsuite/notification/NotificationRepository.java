package io.github.manormachine2207.hrsuite.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Repository for in-app notifications (ADR-017 Stufe 2). RLS scopes reads to the tenant. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientSubjectOrderByCreatedAtDesc(String recipientSubject);

    long countByRecipientSubjectAndReadAtIsNull(String recipientSubject);
}
