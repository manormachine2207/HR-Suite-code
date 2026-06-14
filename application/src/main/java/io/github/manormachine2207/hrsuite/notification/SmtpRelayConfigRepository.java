package io.github.manormachine2207.hrsuite.notification;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the SMTP relay singleton (ADR-019 Stufe 3). */
public interface SmtpRelayConfigRepository extends JpaRepository<SmtpRelayConfig, Short> {
}
