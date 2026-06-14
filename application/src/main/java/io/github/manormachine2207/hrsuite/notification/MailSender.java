package io.github.manormachine2207.hrsuite.notification;

/**
 * Sends a message through a given SMTP relay configuration (ADR-019 Stufe 3).
 * Engine-neutral so the later notification worker (ADR-017 Stufe 2) reuses it.
 * Implementations resolve the password via {@link SecretResolver} (SDR-004) and
 * throw on any delivery failure so callers can record the outcome verbatim.
 */
public interface MailSender {
    void send(SmtpRelayConfig config, MailMessage message);
}
