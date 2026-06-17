package io.github.manormachine2207.hrsuite.notification;

import io.github.manormachine2207.hrsuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String SUBJECT = "dev-applicant~aa";
    private static final String OTHER = "dev-applicant~bb";

    @Mock NotificationRepository repository;
    @Mock EmailOutboxRepository outboxRepository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, outboxRepository);
        TenantContext.set(TENANT);
        lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- in-app notification -------------------------------------------------

    @Test
    void notifyAntragDecidedSavesTenantScopedRecipientNotification() {
        UUID antragId = UUID.randomUUID();

        service.notifyAntragDecided(SUBJECT, antragId, "APPROVED", null, null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getTenantId()).isEqualTo(TENANT);
        assertThat(n.getRecipientSubject()).isEqualTo(SUBJECT);
        assertThat(n.getType()).isEqualTo(NotificationType.ANTRAG_DECIDED);
        assertThat(n.getAntragId()).isEqualTo(antragId);
        assertThat(n.getParams()).containsEntry("status", "APPROVED");
        assertThat(n.isRead()).isFalse();
    }

    // ---- ADR-017 Stufe 2c: outbox enqueue ------------------------------------

    /** Happy path: address + locale known → PENDING outbox row with right fields. */
    @Test
    void decidedEnqueuesOutboxRowWhenEmailKnown() {
        UUID antragId = UUID.randomUUID();

        service.notifyAntragDecided(SUBJECT, antragId, "APPROVED", "user@example.ch", "fr");

        ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(outboxRepository).save(captor.capture());
        EmailOutbox row = captor.getValue();
        assertThat(row.getRecipientEmail()).isEqualTo("user@example.ch");
        assertThat(row.getLocale()).isEqualTo("fr");
        assertThat(row.getTemplateKey()).isEqualTo(EmailTemplates.ANTRAG_DECIDED);
        assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(row.getParams()).containsEntry("status", "APPROVED");
        assertThat(row.getParams()).containsEntry("antragId", antragId.toString());
        assertThat(row.getTenantId()).isEqualTo(TENANT);
    }

    /** No email address → no outbox row. */
    @Test
    void noOutboxRowWhenEmailMissing() {
        service.notifyAntragDecided(SUBJECT, UUID.randomUUID(), "APPROVED", null, "de");

        verifyNoInteractions(outboxRepository);
    }

    /** Blank email address also produces no outbox row. */
    @Test
    void noOutboxRowWhenEmailBlank() {
        service.notifyAntragDecided(SUBJECT, UUID.randomUUID(), "APPROVED", "  ", "de");

        verifyNoInteractions(outboxRepository);
    }

    /** Null locale falls back to "de" (lead language, avoids tenant-module dep). */
    @Test
    void localeFallsBackToDeWhenNull() {
        service.notifyAntragDecided(SUBJECT, UUID.randomUUID(), "APPROVED", "user@example.ch", null);

        ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getLocale()).isEqualTo("de");
    }

    /** Blank locale also falls back to "de". */
    @Test
    void localeFallsBackToDeWhenBlank() {
        service.notifyAntragDecided(SUBJECT, UUID.randomUUID(), "APPROVED", "user@example.ch", "  ");

        ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getLocale()).isEqualTo("de");
    }

    // ---- markRead ------------------------------------------------------------

    @Test
    void markReadOfOwnNotificationSetsReadAt() {
        Notification n = new Notification(UUID.randomUUID(), TENANT, SUBJECT,
                NotificationType.ANTRAG_DECIDED, UUID.randomUUID(), Map.of("status", "APPROVED"));
        lenient().when(repository.findById(n.getId())).thenReturn(Optional.of(n));

        service.markRead(n.getId(), SUBJECT);

        assertThat(n.isRead()).isTrue();
    }

    @Test
    void markReadOfForeignNotificationIsNotFound() {
        Notification n = new Notification(UUID.randomUUID(), TENANT, OTHER,
                NotificationType.ANTRAG_DECIDED, UUID.randomUUID(), Map.of());
        lenient().when(repository.findById(n.getId())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.markRead(n.getId(), SUBJECT))
                .isInstanceOf(NotificationExceptions.NotFound.class);
        assertThat(n.isRead()).isFalse();
    }

    @Test
    void markAllReadMarksEveryUnreadOfTheRecipient() {
        Notification a = new Notification(UUID.randomUUID(), TENANT, SUBJECT,
                NotificationType.ANTRAG_DECIDED, UUID.randomUUID(), Map.of());
        Notification b = new Notification(UUID.randomUUID(), TENANT, SUBJECT,
                NotificationType.ANTRAG_DECIDED, UUID.randomUUID(), Map.of());
        lenient().when(repository.findByRecipientSubjectOrderByCreatedAtDesc(SUBJECT)).thenReturn(List.of(a, b));

        service.markAllRead(SUBJECT);

        assertThat(a.isRead()).isTrue();
        assertThat(b.isRead()).isTrue();
    }
}
