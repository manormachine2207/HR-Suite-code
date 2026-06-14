package io.github.manormachine2207.hrsuite.notification;

/** Transport security for the SMTP relay (ADR-019 Stufe 3). */
public enum SmtpSecurity {
    /** Plain SMTP, no TLS (e.g. Mailpit in dev). */
    NONE,
    /** Opportunistic TLS upgrade on the plain port (typically 587). */
    STARTTLS,
    /** Implicit TLS from the start (typically 465). */
    TLS
}
