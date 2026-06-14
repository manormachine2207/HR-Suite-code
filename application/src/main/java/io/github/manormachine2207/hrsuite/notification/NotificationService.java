package io.github.manormachine2207.hrsuite.notification;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-app notifications (ADR-017 Stufe 2). Created synchronously at status
 * transitions (from the review path) and read by the recipient via the bell.
 * Tenant-scoped through {@link TenantContext} + RLS (ADR-008); the recipient
 * boundary is the {@code recipientSubject} match (same pattern as antrag ownership).
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Records that an applicant's request reached a terminal decision. Called from the
     * review path in the same transaction, so it shares the tenant GUC and rolls back
     * with the decision if anything fails.
     */
    public void notifyAntragDecided(String recipientSubject, UUID antragId, String status) {
        Notification n = new Notification(
                UuidCreator.getTimeOrderedEpoch(), TenantContext.require(), recipientSubject,
                NotificationType.ANTRAG_DECIDED, antragId, Map.of("status", status));
        repository.save(n);
    }

    @Transactional(readOnly = true)
    public List<Notification> listOwn(String subject) {
        return repository.findByRecipientSubjectOrderByCreatedAtDesc(subject);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String subject) {
        return repository.countByRecipientSubjectAndReadAtIsNull(subject);
    }

    /** Marks one own notification read; a foreign one is reported as NotFound (no leak). */
    public void markRead(UUID id, String subject) {
        Notification n = repository.findById(id)
                .filter(x -> x.getRecipientSubject().equals(subject))
                .orElseThrow(() -> new NotificationExceptions.NotFound("notification not found: " + id));
        n.markRead();
    }

    public void markAllRead(String subject) {
        for (Notification n : repository.findByRecipientSubjectOrderByCreatedAtDesc(subject)) {
            n.markRead();
        }
    }
}
