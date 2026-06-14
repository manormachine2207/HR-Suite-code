package io.github.manormachine2207.hrsuite.notification;

/** A minimal outbound message (ADR-019 Stufe 3). HTML body is optional. */
public record MailMessage(String to, String subject, String bodyText, String bodyHtml) {
    public static MailMessage text(String to, String subject, String bodyText) {
        return new MailMessage(to, subject, bodyText, null);
    }
}
