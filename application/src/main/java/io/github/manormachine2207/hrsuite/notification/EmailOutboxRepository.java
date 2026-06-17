package io.github.manormachine2207.hrsuite.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the e-mail outbox (ADR-017 Stufe 2c). System-scope — no RLS.
 *
 * <p>The background worker (later task) uses the query methods below to poll for
 * rows that need processing.
 */
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    /**
     * Returns all rows with the given status, oldest first.
     * Typical use: worker polls for {@link EmailOutboxStatus#PENDING} on the happy path.
     */
    List<EmailOutbox> findByStatusOrderByCreatedAtAsc(EmailOutboxStatus status);

    /**
     * Returns all rows whose status is in the given collection, oldest first.
     * Typical use: worker polls for {@code [PENDING, FAILED]} to catch both first
     * attempts and retries in a single query.
     */
    List<EmailOutbox> findByStatusInOrderByCreatedAtAsc(Collection<EmailOutboxStatus> statuses);
}
