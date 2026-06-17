package io.github.manormachine2207.hrsuite.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.manormachine2207.hrsuite.notification.EmailTemplates.ANTRAG_DECIDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link EmailOutboxWorker} (ADR-017 Stufe 2c).
 * No Spring context — worker is constructed directly; {@link #worker#processOutbox()} is called.
 */
@ExtendWith(MockitoExtension.class)
class EmailOutboxWorkerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final String RECIPIENT = "user@example.ch";
    private static final Map<String, Object> PARAMS = Map.of(
            "status", "APPROVED",
            "antragId", UUID.randomUUID().toString());

    @Mock EmailOutboxRepository repo;
    @Mock SmtpRelayService smtpRelayService;
    @Mock MailSender mailSender;

    private EmailOutboxWorker worker;

    /** An enabled relay config stub, built via the same pattern as SmtpRelayServiceTest. */
    private SmtpRelayConfig enabledRelay;
    /** A disabled relay config stub. */
    private SmtpRelayConfig disabledRelay;

    @BeforeEach
    void setUp() {
        worker = new EmailOutboxWorker(repo, smtpRelayService, mailSender);

        enabledRelay = new SmtpRelayConfig();
        enabledRelay.update("mailpit", 1025, SmtpSecurity.NONE, "no-reply@hr-suite.local",
                null, null, null, true);

        disabledRelay = new SmtpRelayConfig();
        disabledRelay.update("mailpit", 1025, SmtpSecurity.NONE, "no-reply@hr-suite.local",
                null, null, null, false);

        lenient().when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── 1. Happy path: sends and marks SENT ──────────────────────────────────

    @Test
    void sendsAndMarksSent_whenRelayEnabledAndRowPending() {
        EmailOutbox row = pendingRow("de");
        when_relayStubbedEnabled_andRepoReturns(row);

        worker.processOutbox();

        // mailSender called with the right recipient
        ArgumentCaptor<MailMessage> msgCaptor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailSender).send(any(), msgCaptor.capture());
        assertThat(msgCaptor.getValue().to()).isEqualTo(RECIPIENT);

        // row saved with SENT
        ArgumentCaptor<EmailOutbox> rowCaptor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(repo).save(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(rowCaptor.getValue().getSentAt()).isNotNull();
    }

    // ── 2. Relay disabled → no send, no save ─────────────────────────────────

    @Test
    void doesNothing_whenRelayDisabled() {
        when(smtpRelayService.get()).thenReturn(disabledRelay);

        worker.processOutbox();

        verifyNoInteractions(mailSender);
        verify(repo, never()).save(any());
    }

    // ── 3. Send failure → FAILED then DEAD at max_attempts ───────────────────

    @Test
    void marksFailed_onSendError_andDeadAtMaxAttempts() {
        doThrow(new RuntimeException("Connection timed out")).when(mailSender).send(any(), any());

        // Row seeded at attempts=4; maxAttempts defaults to 5
        // so one more failure must flip to DEAD
        EmailOutbox nearMaxRow = pendingRowWithAttempts("de", 4);
        when_relayStubbedEnabled_andRepoReturns(nearMaxRow);

        worker.processOutbox();

        ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(repo).save(captor.capture());
        EmailOutbox saved = captor.getValue();
        assertThat(saved.getAttempts()).isEqualTo(5);
        assertThat(saved.getStatus()).isEqualTo(EmailOutboxStatus.DEAD);
        assertThat(saved.getLastError()).contains("Connection timed out");
    }

    @Test
    void marksFailed_notDead_whenBelowMaxAttempts() {
        doThrow(new RuntimeException("SMTP reject")).when(mailSender).send(any(), any());

        EmailOutbox row = pendingRowWithAttempts("de", 1);  // 1 attempt so far, max=5
        when_relayStubbedEnabled_andRepoReturns(row);

        worker.processOutbox();

        ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
    }

    // ── 4. Locale picks the right template ───────────────────────────────────

    @Test
    void frLocale_producesFrencheSubject() {
        EmailOutbox row = pendingRow("fr");
        when_relayStubbedEnabled_andRepoReturns(row);

        worker.processOutbox();

        ArgumentCaptor<MailMessage> msgCaptor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailSender).send(any(), msgCaptor.capture());
        MailMessage sent = msgCaptor.getValue();

        // Compare against EmailTemplates directly (source of truth)
        MailMessage expected = EmailTemplates.render(ANTRAG_DECIDED, "fr", RECIPIENT, PARAMS);
        assertThat(sent.subject()).isEqualTo(expected.subject());
        assertThat(sent.subject()).contains("demande");  // FR key word
    }

    // ── 5. One bad row doesn't abort the batch ────────────────────────────────

    @Test
    void oneBadRow_doesNotAbortBatch_secondRowStillSent() {
        EmailOutbox bad  = pendingRow("de");
        EmailOutbox good = pendingRow("en");

        when(smtpRelayService.get()).thenReturn(enabledRelay);
        when(repo.findByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(bad, good));

        // First send throws, second succeeds
        doThrow(new RuntimeException("temporary failure"))
                .doNothing()
                .when(mailSender).send(any(), any());

        worker.processOutbox();

        // Both rows must have been saved
        verify(repo, times(2)).save(any(EmailOutbox.class));
        assertThat(bad.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(good.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
    }

    @Test
    void processOutbox_doesNotThrow_whenSendFails() {
        doThrow(new RuntimeException("boom")).when(mailSender).send(any(), any());
        when_relayStubbedEnabled_andRepoReturns(pendingRow("de"));

        // Must not propagate any exception
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> worker.processOutbox());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EmailOutbox pendingRow(String locale) {
        return new EmailOutbox(UUID.randomUUID(), TENANT, RECIPIENT, locale, ANTRAG_DECIDED, PARAMS);
    }

    /**
     * Creates a PENDING row and then simulates {@code priorAttempts} failed attempts
     * so {@code row.getAttempts()} equals {@code priorAttempts} without changing to DEAD.
     * We do this by calling markAttemptFailed below the threshold, then resetting status
     * to PENDING via a fresh row with pre-set attempts — only possible if we simply
     * reflect; instead we create a row and let markAttemptFailed drive it to FAILED at
     * sub-max counts, and to PENDING at 0.
     *
     * <p>Implementation note: since {@link EmailOutbox} has no setter for {@code attempts},
     * we drive it by calling {@code markAttemptFailed} repeatedly on a fresh row
     * (which resets status to FAILED — fine for a FAILED retry scenario) and then
     * treat it as a FAILED row that the worker will pick up. The worker itself re-fetches
     * PENDING+FAILED, so pre-seeding as FAILED is the correct test setup.
     */
    private EmailOutbox pendingRowWithAttempts(String locale, int priorAttempts) {
        EmailOutbox row = pendingRow(locale);
        for (int i = 0; i < priorAttempts; i++) {
            row.markAttemptFailed("prior error", java.time.OffsetDateTime.now());
        }
        return row;
    }

    private void when_relayStubbedEnabled_andRepoReturns(EmailOutbox row) {
        when(smtpRelayService.get()).thenReturn(enabledRelay);
        when(repo.findByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(row));
    }
}
