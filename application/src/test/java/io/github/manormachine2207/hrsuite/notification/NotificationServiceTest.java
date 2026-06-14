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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String SUBJECT = "dev-applicant~aa";
    private static final String OTHER = "dev-applicant~bb";

    @Mock NotificationRepository repository;
    @Mock SmtpRelayService smtpRelayService;
    @Mock MailSender mailSender;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, smtpRelayService, mailSender);
        TenantContext.set(TENANT);
        lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private SmtpRelayConfig relay(boolean enabled) {
        SmtpRelayConfig c = new SmtpRelayConfig();
        c.update("mailpit", 1025, SmtpSecurity.NONE, "no-reply@hr-suite.local", null, null, null, enabled);
        return c;
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void notifyAntragDecidedSavesTenantScopedRecipientNotification() {
        UUID antragId = UUID.randomUUID();

        service.notifyAntragDecided(SUBJECT, antragId, "APPROVED", null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getTenantId()).isEqualTo(TENANT);
        assertThat(n.getRecipientSubject()).isEqualTo(SUBJECT);
        assertThat(n.getType()).isEqualTo(NotificationType.ANTRAG_DECIDED);
        assertThat(n.getAntragId()).isEqualTo(antragId);
        assertThat(n.getParams()).containsEntry("status", "APPROVED");
        assertThat(n.isRead()).isFalse();
    }

    // ---- ADR-017 Stufe 2b: best-effort e-mail ------------------------------

    @Test
    void emailSentWhenRelayEnabledAndAddressKnown() {
        when(smtpRelayService.get()).thenReturn(relay(true));

        service.notifyAntragDecided(SUBJECT, UUID.randomUUID(), "APPROVED", "user@example.ch");

        org.mockito.ArgumentCaptor<MailMessage> msg = org.mockito.ArgumentCaptor.forClass(MailMessage.class);
        org.mockito.Mockito.verify(mailSender).send(any(), msg.capture());
        assertThat(msg.getValue().to()).isEqualTo("user@example.ch");
        assertThat(msg.getValue().bodyText()).contains("APPROVED");
    }

    @Test
    void noEmailWhenRelayDisabled() {
        when(smtpRelayService.get()).thenReturn(relay(false));

        service.notifyAntragDecided(SUBJECT, UUID.randomUUID(), "APPROVED", "user@example.ch");

        org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.never()).send(any(), any());
    }

    @Test
    void noEmailWhenAddressMissing() {
        service.notifyAntragDecided(SUBJECT, UUID.randomUUID(), "APPROVED", null);

        org.mockito.Mockito.verifyNoInteractions(smtpRelayService);
        org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.never()).send(any(), any());
    }

    /** A mail failure is swallowed — the in-app notification (and the decision) stand. */
    @Test
    void mailFailureDoesNotPropagate() {
        when(smtpRelayService.get()).thenReturn(relay(true));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(), any());

        service.notifyAntragDecided(SUBJECT, UUID.randomUUID(), "APPROVED", "user@example.ch");

        org.mockito.Mockito.verify(repository).save(any());   // in-app notification still saved
    }

    @Test
    void markReadOfOwnNotificationSetsReadAt() {
        Notification n = new Notification(UUID.randomUUID(), TENANT, SUBJECT,
                NotificationType.ANTRAG_DECIDED, UUID.randomUUID(), Map.of("status", "APPROVED"));
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));

        service.markRead(n.getId(), SUBJECT);

        assertThat(n.isRead()).isTrue();
    }

    @Test
    void markReadOfForeignNotificationIsNotFound() {
        Notification n = new Notification(UUID.randomUUID(), TENANT, OTHER,
                NotificationType.ANTRAG_DECIDED, UUID.randomUUID(), Map.of());
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));

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
        when(repository.findByRecipientSubjectOrderByCreatedAtDesc(SUBJECT)).thenReturn(List.of(a, b));

        service.markAllRead(SUBJECT);

        assertThat(a.isRead()).isTrue();
        assertThat(b.isRead()).isTrue();
    }
}
