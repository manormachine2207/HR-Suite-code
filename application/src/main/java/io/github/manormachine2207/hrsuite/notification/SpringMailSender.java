package io.github.manormachine2207.hrsuite.notification;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * {@link MailSender} backed by Spring's JavaMailSender (ADR-019 Stufe 3). The
 * sender is built PER SEND from the stored relay config (runtime-configurable, not
 * application.yml), and the password is resolved from the environment via
 * {@link SecretResolver} (SDR-004) — it never touches the DB, logs or API.
 */
@Component
public class SpringMailSender implements MailSender {

    private final SecretResolver secretResolver;

    public SpringMailSender(SecretResolver secretResolver) {
        this.secretResolver = secretResolver;
    }

    @Override
    public void send(SmtpRelayConfig config, MailMessage message) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getHost());
        mailSender.setPort(config.getPort());

        boolean auth = config.getUsername() != null && !config.getUsername().isBlank();
        if (auth) {
            mailSender.setUsername(config.getUsername());
            // SDR-004: password comes from the env var named by passwordRef, never the DB.
            secretResolver.resolve(config.getPasswordRef()).ifPresent(mailSender::setPassword);
        }

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", Boolean.toString(auth));
        switch (config.getSecurity()) {
            case STARTTLS -> props.put("mail.smtp.starttls.enable", "true");
            case TLS -> props.put("mail.smtp.ssl.enable", "true");
            case NONE -> { /* plain transport (e.g. Mailpit) */ }
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(formatFrom(config));
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.bodyText());
        mailSender.send(mail);
    }

    private static String formatFrom(SmtpRelayConfig config) {
        if (config.getFromName() == null || config.getFromName().isBlank()) {
            return config.getFromAddress();
        }
        return config.getFromName() + " <" + config.getFromAddress() + ">";
    }
}
