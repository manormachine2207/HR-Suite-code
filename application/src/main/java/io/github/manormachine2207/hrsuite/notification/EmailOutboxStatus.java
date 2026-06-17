package io.github.manormachine2207.hrsuite.notification;

/**
 * Lifecycle states for an {@link EmailOutbox} row (ADR-017 Stufe 2c).
 *
 * <ul>
 *   <li>{@code PENDING}  — waiting to be picked up by the worker.</li>
 *   <li>{@code SENT}     — SMTP accepted the message; terminal success.</li>
 *   <li>{@code FAILED}   — last attempt failed; will be retried up to {@code maxAttempts}.</li>
 *   <li>{@code DEAD}     — {@code attempts >= maxAttempts}; no further retries; requires manual
 *       intervention.</li>
 * </ul>
 */
public enum EmailOutboxStatus {
    PENDING,
    SENT,
    FAILED,
    DEAD
}
