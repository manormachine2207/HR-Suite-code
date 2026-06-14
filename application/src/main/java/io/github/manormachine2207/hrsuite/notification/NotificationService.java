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
 */
@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final SmtpRelayService smtpRelayService;
    private final MailSender mailSender;

    public NotificationService(NotificationRepository repository,
                               SmtpRelayService smtpRelayService, MailSender mailSender) {
        this.repository = repository;
        this.smtpRelayService = smtpRelayService;
        this.mailSender = mailSender;
    }

    /**
     * Records that an applicant's request reached a terminal decision. Called from the
     * review path in the same transaction, so the IN-APP notification shares the tenant
     * GUC and rolls back with the decision. The optional e-mail (ADR-017 Stufe 2b) is a
     * best-effort last step: gated by an enabled relay + a known recipient address, and
     * caught so a mail failure NEVER rolls back the decision.
     */
    public void notifyAntragDecided(String recipientSubject, UUID antragId, String status,
                                    String recipientEmail) {
        Notification n = new Notification(
                UuidCreator.getTimeOrderedEpoch(), TenantContext.require(), recipientSubject,
                NotificationType.ANTRAG_DECIDED, antragId, Map.of("status", status));
        repository.save(n);
        sendEmailBestEffort(antragId, status, recipientEmail);
    }

    /** Sends the decision e-mail if the relay is enabled and an address is known; never throws. */
    private void sendEmailBestEffort(UUID antragId, String status, String recipientEmail) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }
        try {
            SmtpRelayConfig relay = smtpRelayService.get();
            if (!relay.isEnabled()) {
                return;
            }
            MailMessage msg = MailMessage.text(recipientEmail,
                    "HR-Suite: Ihr Antrag wurde entschieden",
                    "Guten Tag\n\nIhr Antrag wurde entschieden: " + status + ".\n\n"
                            + "Details finden Sie in der HR-Suite unter \"Meine Anträge\" "
                            + "(Antrag-Referenz " + antragId + ").\n\n"
                            + "Freundliche Grüsse\nHR-Suite");
            mailSender.send(relay, msg);
            log.info("notification.email.sent antrag={} status={}", antragId, status);
        } catch (RuntimeException e) {
            // Best-effort (ADR-017 Stufe 2b): the in-app notification + decision stand.
            log.warn("notification.email.failed antrag={} error={}", antragId, e.getMessage());
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
