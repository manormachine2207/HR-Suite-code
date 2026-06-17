package io.github.manormachine2207.hrsuite.notification;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p>ADR-017 Stufe 2c: decision e-mails are no longer sent synchronously here;
 * instead a {@link EmailOutbox} row is enqueued inside the same transaction.
 * The background worker (task C2-4) picks up PENDING rows and sends them.
 * {@link SmtpRelayService} and {@link MailSender} are intentionally NOT injected
 * here — they live in the SMTP relay configuration / worker path only.
 */
@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final EmailOutboxRepository outboxRepository;

    public NotificationService(NotificationRepository repository,
                               EmailOutboxRepository outboxRepository) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Records that an applicant's request reached a terminal decision. Called from the
     * review path in the same transaction, so the IN-APP notification and the outbox row
     * share the tenant GUC and roll back with the decision if anything fails.
     *
     * <p>If {@code recipientEmail} is known, a {@link EmailOutbox} row in state PENDING
     * is enqueued for the background worker (ADR-017 Stufe 2c).
     *
     * @param recipientLocale BCP-47 locale; falls back to {@code "de"} when null/blank.
     *     Note: ideally we would read {@code tenant.default_locale} but that would
     *     introduce a notification→tenant module dependency. "de" is the lead language
     *     per Tenet 6 / BDR-005 and a safe fallback until a tenant-locale lookup is added.
     */
    public void notifyAntragDecided(String recipientSubject, UUID antragId, String status,
                                    String recipientEmail, String recipientLocale) {
        Notification n = new Notification(
                UuidCreator.getTimeOrderedEpoch(), TenantContext.require(), recipientSubject,
                NotificationType.ANTRAG_DECIDED, antragId, Map.of("status", status));
        repository.save(n);

        if (recipientEmail != null && !recipientEmail.isBlank()) {
            String locale = (recipientLocale != null && !recipientLocale.isBlank())
                    ? recipientLocale : "de";
            EmailOutbox outbox = new EmailOutbox(
                    UuidCreator.getTimeOrderedEpoch(),
                    TenantContext.require(),
                    recipientEmail,
                    locale,
                    EmailTemplates.ANTRAG_DECIDED,
                    Map.of("status", status, "antragId", antragId.toString()));
            outboxRepository.save(outbox);
            log.info("notification.outbox.enqueued antrag={} status={} locale={}", antragId, status, locale);
        }
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
