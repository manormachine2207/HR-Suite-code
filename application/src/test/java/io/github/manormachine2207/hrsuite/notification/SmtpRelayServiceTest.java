package io.github.manormachine2207.hrsuite.notification;

import io.github.manormachine2207.hrsuite.notification.dto.TestSendResponse;
import io.github.manormachine2207.hrsuite.notification.dto.UpdateSmtpRelayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpRelayServiceTest {

    @Mock SmtpRelayConfigRepository repository;
    @Mock MailSender mailSender;
    @Mock SecretResolver secretResolver;

    private SmtpRelayService service;
    private SmtpRelayConfig config;

    @BeforeEach
    void setUp() {
        service = new SmtpRelayService(repository, mailSender, secretResolver);
        config = new SmtpRelayConfig();
        config.update("mailpit", 1025, SmtpSecurity.NONE, "no-reply@hr-suite.local",
                null, null, null, false);
        lenient().when(repository.findById(SmtpRelayConfig.SINGLETON_ID)).thenReturn(Optional.of(config));
        lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void updateAppliesFieldsAndResetsVerified() {
        config.markTested(true, "success", java.time.OffsetDateTime.now());   // pretend it was verified

        SmtpRelayConfig result = service.update(new UpdateSmtpRelayRequest(
                "smtp.example.ch", 587, SmtpSecurity.STARTTLS, "hr@example.ch", "HR",
                "hr-user", "HRSUITE_SMTP_PASSWORD", true));

        assertThat(result.getHost()).isEqualTo("smtp.example.ch");
        assertThat(result.getPort()).isEqualTo(587);
        assertThat(result.getSecurity()).isEqualTo(SmtpSecurity.STARTTLS);
        assertThat(result.getPasswordRef()).isEqualTo("HRSUITE_SMTP_PASSWORD");
        assertThat(result.isVerified()).isFalse();   // changed config must be re-tested
    }

    @Test
    void testSendSuccessMarksVerified() {
        TestSendResponse res = service.testSend("admin@example.ch");

        assertThat(res.success()).isTrue();
        assertThat(res.outcome()).isEqualTo("success");
        assertThat(config.isVerified()).isTrue();
        assertThat(config.getLastTestAt()).isNotNull();
    }

    @Test
    void testSendFailureRecordsOutcomeVerbatimAndStaysUnverified() {
        doThrow(new RuntimeException("Connection refused")).when(mailSender).send(any(), any());

        TestSendResponse res = service.testSend("admin@example.ch");

        assertThat(res.success()).isFalse();
        assertThat(res.outcome()).contains("Connection refused");
        assertThat(config.isVerified()).isFalse();
        assertThat(config.getLastTestOutcome()).startsWith("error:");
    }

    @Test
    void secretConfiguredDelegatesToResolver() {
        config.update("mailpit", 587, SmtpSecurity.STARTTLS, "a@b.ch", null, "u",
                "HRSUITE_SMTP_PASSWORD", true);
        when(secretResolver.isConfigured("HRSUITE_SMTP_PASSWORD")).thenReturn(true);

        assertThat(service.secretConfigured()).isTrue();
    }
}
