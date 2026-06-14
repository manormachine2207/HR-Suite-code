package io.github.manormachine2207.hrsuite.notification.dto;

import io.github.manormachine2207.hrsuite.notification.SmtpRelayConfig;
import io.github.manormachine2207.hrsuite.notification.SmtpSecurity;

import java.time.OffsetDateTime;

/**
 * Read model of the SMTP relay config (ADR-019 Stufe 3). Carries the password
 * REFERENCE name + a {@code secretConfigured} flag — never the password value
 * (SDR-004).
 */
public record SmtpRelayResponse(
        String host,
        int port,
        SmtpSecurity security,
        String fromAddress,
        String fromName,
        String username,
        String passwordRef,
        boolean secretConfigured,
        boolean enabled,
        boolean verified,
        OffsetDateTime lastTestAt,
        String lastTestOutcome) {

    public static SmtpRelayResponse from(SmtpRelayConfig c, boolean secretConfigured) {
        return new SmtpRelayResponse(c.getHost(), c.getPort(), c.getSecurity(),
                c.getFromAddress(), c.getFromName(), c.getUsername(), c.getPasswordRef(),
                secretConfigured, c.isEnabled(), c.isVerified(), c.getLastTestAt(),
                c.getLastTestOutcome());
    }
}
