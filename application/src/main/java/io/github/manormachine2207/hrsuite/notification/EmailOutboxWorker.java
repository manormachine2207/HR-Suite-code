package io.github.manormachine2207.hrsuite.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Background worker that drains the {@link EmailOutbox} table (ADR-017 Stufe 2c).
 *
 * <p>Runs without tenant context — {@code email_outbox} and {@code smtp_relay_config}
 * are system-scope tables with no RLS policy attached; no GUC must be set.
 *
 * <p>Flow per row:
 * <ol>
 *   <li>{@code PENDING} / {@code FAILED} → picked up by
 *       {@link EmailOutboxRepository#findByStatusInOrderByCreatedAtAsc}.</li>
 *   <li>Template rendered via {@link EmailTemplates#render}, message handed to
 *       {@link MailSender#send}.</li>
 *   <li>Success → {@link EmailOutbox#markSent} → status {@code SENT} (terminal).</li>
 *   <li>Failure → {@link EmailOutbox#markAttemptFailed}: increments {@code attempts};
 *       stays {@code FAILED} until {@code attempts >= maxAttempts}, then
 *       {@code DEAD} (terminal, no further retries).</li>
 *   <li>One bad row never aborts the batch — each row has its own try/catch.</li>
 * </ol>
 *
 * <p>When the relay is disabled ({@link SmtpRelayConfig#isEnabled()} == false) the
 * worker returns immediately and leaves all rows {@code PENDING}.
 */
@Component
public class EmailOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxWorker.class);

    private final EmailOutboxRepository repo;
    private final SmtpRelayService smtpRelayService;
    private final MailSender mailSender;

    public EmailOutboxWorker(EmailOutboxRepository repo,
                             SmtpRelayService smtpRelayService,
                             MailSender mailSender) {
        this.repo = repo;
        this.smtpRelayService = smtpRelayService;
        this.mailSender = mailSender;
    }

    /**
     * Scheduled entry point — delegates to {@link #processOutbox()} so the logic can
     * be invoked directly in tests without the scheduling infrastructure.
     */
    @Scheduled(fixedDelayString = "${hrsuite.email.worker.delay-ms:15000}")
    public void drainOutbox() {
        processOutbox();
    }

    /**
     * Processes all {@code PENDING} and {@code FAILED} outbox rows in a single
     * transaction. Each row is saved individually; a per-row failure does not prevent
     * the remaining rows from being processed.
     */
    @Transactional
    public void processOutbox() {
        SmtpRelayConfig relay = smtpRelayService.get();
        if (!relay.isEnabled()) {
            log.debug("email.worker.skip relay_disabled=true");
            return;
        }

        List<EmailOutbox> rows = repo.findByStatusInOrderByCreatedAtAsc(
                List.of(EmailOutboxStatus.PENDING, EmailOutboxStatus.FAILED));

        log.debug("email.worker.drain rows={}", rows.size());

        for (EmailOutbox row : rows) {
            try {
                MailMessage msg = EmailTemplates.render(
                        row.getTemplateKey(),
                        row.getLocale(),
                        row.getRecipientEmail(),
                        row.getParams());
                mailSender.send(relay, msg);
                row.markSent(OffsetDateTime.now());
                repo.save(row);
                log.info("email.worker.sent id={} to={}", row.getId(), row.getRecipientEmail());
            } catch (RuntimeException e) {
                row.markAttemptFailed(e.getMessage(), OffsetDateTime.now());
                repo.save(row);
                log.warn("email.worker.failed id={} attempts={} status={} error={}",
                        row.getId(), row.getAttempts(), row.getStatus(), e.getMessage());
            }
        }
    }
}
