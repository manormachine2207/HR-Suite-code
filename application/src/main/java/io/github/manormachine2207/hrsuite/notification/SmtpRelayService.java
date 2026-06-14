package io.github.manormachine2207.hrsuite.notification;

import io.github.manormachine2207.hrsuite.notification.dto.TestSendResponse;
import io.github.manormachine2207.hrsuite.notification.dto.UpdateSmtpRelayRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Application service for the global SMTP relay (ADR-019 Stufe 3): read/update the
 * singleton config and run a test send. The password is never read here — only its
 * env-var reference (SDR-004). A config change resets the verified flag; a
 * successful test send sets it.
 */
@Service
@Transactional
public class SmtpRelayService {

    private static final Logger log = LoggerFactory.getLogger(SmtpRelayService.class);

    private final SmtpRelayConfigRepository repository;
    private final MailSender mailSender;
    private final SecretResolver secretResolver;

    public SmtpRelayService(SmtpRelayConfigRepository repository, MailSender mailSender,
                            SecretResolver secretResolver) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.secretResolver = secretResolver;
    }

    @Transactional(readOnly = true)
    public SmtpRelayConfig get() {
        return repository.findById(SmtpRelayConfig.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "smtp_relay_config singleton row is missing (migration 012 seeds it)"));
    }

    /** True if the password env var named by the current config is set (without revealing it). */
    @Transactional(readOnly = true)
    public boolean secretConfigured() {
        return secretResolver.isConfigured(get().getPasswordRef());
    }

    public SmtpRelayConfig update(UpdateSmtpRelayRequest req) {
        SmtpRelayConfig config = get();
        config.update(req.host(), req.port(), req.security(), req.fromAddress(),
                req.fromName(), req.username(), req.passwordRef(), req.enabled());
        return repository.save(config);
    }

    /**
     * Sends a test mail through the current relay and records the outcome. The relay
     * need not be {@code enabled} (testing precedes enabling); a delivery failure is
     * captured verbatim (never a secret) and leaves {@code verified=false}.
     */
    public TestSendResponse testSend(String to) {
        SmtpRelayConfig config = get();
        MailMessage message = MailMessage.text(to,
                "HR-Suite SMTP-Relay-Test",
                "Dies ist eine Test-E-Mail der HR-Suite Plattform-Verwaltung. "
                        + "Wenn Sie sie erhalten, ist der SMTP-Relay korrekt konfiguriert.");
        OffsetDateTime now = OffsetDateTime.now();
        try {
            mailSender.send(config, message);
            config.markTested(true, "success", now);
            repository.save(config);
            log.info("smtp.test.send success host={} port={} to={}",
                    config.getHost(), config.getPort(), to);
            return new TestSendResponse(true, "success");
        } catch (RuntimeException e) {
            String outcome = "error: " + e.getMessage();
            config.markTested(false, truncate(outcome), now);
            repository.save(config);
            log.warn("smtp.test.send failed host={} port={} error={}",
                    config.getHost(), config.getPort(), e.getMessage());
            return new TestSendResponse(false, outcome);
        }
    }

    private static String truncate(String s) {
        return s.length() <= 512 ? s : s.substring(0, 512);
    }
}
