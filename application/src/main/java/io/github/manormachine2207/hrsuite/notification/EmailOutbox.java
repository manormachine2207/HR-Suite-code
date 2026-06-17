package io.github.manormachine2207.hrsuite.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox row for an e-mail that must be sent asynchronously (ADR-017 Stufe 2c).
 *
 * <p>System-scope (no RLS) — this is a worker queue, not a tenant API. {@code tenantId}
 * is stored as data so the worker can contextualise the send, but no RLS policy is
 * attached (same rationale as {@link SmtpRelayConfig}).
 *
 * <p>The background worker (later task) polls for {@link EmailOutboxStatus#PENDING} /
 * {@link EmailOutboxStatus#FAILED} rows, renders the template via {@code templateKey} +
 * {@code params}, and calls {@link #markSent} or {@link #markAttemptFailed}.
 */
@Entity
@Table(name = "email_outbox")
public class EmailOutbox {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "recipient_email", nullable = false, updatable = false, length = 320)
    private String recipientEmail;

    /** BCP-47 locale tag (e.g. {@code de}, {@code fr}, {@code it}, {@code en}) used to render the template. */
    @Column(name = "locale", nullable = false, updatable = false, length = 5)
    private String locale;

    /** Identifies which e-mail template to render (e.g. {@code decision.approved}). */
    @Column(name = "template_key", nullable = false, updatable = false, length = 64)
    private String templateKey;

    /** Template variables — a small jsonb bag (BDR-005). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> params;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EmailOutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    protected EmailOutbox() {
        // for JPA
    }

    /**
     * Creates a new outbox entry in {@link EmailOutboxStatus#PENDING} state.
     *
     * @param id           app-assigned UUID v7 (use {@code UuidCreator.getTimeOrderedEpoch()})
     * @param tenantId     tenant this e-mail belongs to
     * @param recipientEmail destination address (max 320 chars, RFC 5321)
     * @param locale       BCP-47 locale for template rendering
     * @param templateKey  logical template identifier
     * @param params       template variables (may be null → empty map)
     */
    public EmailOutbox(UUID id, UUID tenantId, String recipientEmail, String locale,
                       String templateKey, Map<String, Object> params) {
        this.id = id;
        this.tenantId = tenantId;
        this.recipientEmail = recipientEmail;
        this.locale = locale;
        this.templateKey = templateKey;
        this.params = params == null ? Map.of() : params;
        this.status = EmailOutboxStatus.PENDING;
        this.attempts = 0;
        this.maxAttempts = 5;
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * Records a successful SMTP send — terminal success.
     *
     * @param at the timestamp the SMTP server accepted the message
     */
    public void markSent(OffsetDateTime at) {
        this.status = EmailOutboxStatus.SENT;
        this.sentAt = at;
    }

    /**
     * Records a failed delivery attempt.
     * Increments {@code attempts}; transitions to {@link EmailOutboxStatus#DEAD} if
     * {@code attempts >= maxAttempts}, otherwise stays / moves to
     * {@link EmailOutboxStatus#FAILED} for a future retry.
     *
     * @param error the error message (truncated to 512 chars if longer)
     * @param at    timestamp of the attempt
     */
    public void markAttemptFailed(String error, OffsetDateTime at) {
        this.attempts++;
        this.lastError = truncate(error, 512);
        this.lastAttemptAt = at;
        this.status = this.attempts >= this.maxAttempts
                ? EmailOutboxStatus.DEAD
                : EmailOutboxStatus.FAILED;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getLocale() { return locale; }
    public String getTemplateKey() { return templateKey; }
    public Map<String, Object> getParams() { return params == null ? Map.of() : params; }
    public EmailOutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastAttemptAt() { return lastAttemptAt; }
    public OffsetDateTime getSentAt() { return sentAt; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
